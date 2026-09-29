package com.bsolz.lms.reporting.service;

import com.bsolz.lms.leave.model.enums.LeaveStatus;
import com.bsolz.lms.reporting.domain.ReportScope;
import com.bsolz.lms.reporting.web.dto.BalanceRow;
import com.bsolz.lms.reporting.web.dto.DashboardSummaryResponse;
import com.bsolz.lms.reporting.web.dto.LeaveHistoryRow;
import com.bsolz.lms.reporting.web.dto.LeaveTypeRef;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The reports' SQL: read-only queries over the tenant schema (the connection's {@code search_path} is
 * the bound tenant's). "Days taken" are approved days - including leave whose cancellation is awaiting
 * approval, which stands until then - counted per calendar day, so a request spanning the range's edge
 * counts only its days inside it.
 */
@Component
@RequiredArgsConstructor
class ReportQueries {

	private static final String TAKEN = "r.status IN ('APPROVED', 'CANCELLATION_PENDING') AND d.amount > 0";

	private static final String IN_SCOPE = "IN (SELECT id FROM scope_employee)";

	private final NamedParameterJdbcTemplate jdbc;

	/** Days taken per {@code yyyy-MM} month and leave type. */
	List<MonthTypeDays> daysByMonthAndType(ReportScope scope, LocalDate from, LocalDate to) {
		return jdbc.query(ReportScope.CTE + """
				SELECT to_char(d.leave_date, 'YYYY-MM') AS month, t.id, t.code, t.name, t.color, SUM(d.amount) AS days
				FROM leave_request r
				JOIN leave_request_day d ON d.leave_request_id = r.id
				JOIN leave_type t ON t.id = r.leave_type_id
				WHERE %s AND d.leave_date BETWEEN :from AND :to AND r.employee_id %s
				GROUP BY 1, t.id, t.code, t.name, t.color, t.sort_order
				ORDER BY 1, t.sort_order, t.name
				""".formatted(TAKEN, IN_SCOPE), params(scope).addValue("from", from).addValue("to", to),
				(rs, row) -> new MonthTypeDays(rs.getString("month"), leaveType(rs), rs.getBigDecimal("days")));
	}

	/** Submissions, approvals and rejections per {@code yyyy-MM} month (in the zone). */
	List<MonthActionCount> actionsByMonth(ReportScope scope, LocalDate from, LocalDate to, ZoneId zone) {
		return jdbc.query(ReportScope.CTE + """
				SELECT to_char(h.created_at AT TIME ZONE :zone, 'YYYY-MM') AS month, h.action, count(*) AS n
				FROM leave_request_history h
				JOIN leave_request r ON r.id = h.leave_request_id
				WHERE h.action IN ('SUBMIT', 'APPROVE', 'REJECT')
				  AND (h.created_at AT TIME ZONE :zone)::date BETWEEN :from AND :to AND r.employee_id %s
				GROUP BY 1, 2
				""".formatted(IN_SCOPE),
				params(scope).addValue("from", from).addValue("to", to).addValue("zone", zone.getId()),
				(rs, row) -> new MonthActionCount(rs.getString("month"), rs.getString("action"), rs.getInt("n")));
	}

	List<LeaveHistoryRow> history(ReportScope scope, ReportCriteria criteria, int limit, long offset) {
		MapSqlParameterSource params = historyParams(scope, criteria).addValue("limit", limit).addValue("offset", offset);
		return jdbc.query(ReportScope.CTE + """
				SELECT r.id, e.id AS employee_id, e.employee_code, e.first_name || ' ' || e.last_name AS employee_name,
				       dep.name AS department_name, t.id AS type_id, t.code, t.name, t.color, r.start_date, r.end_date,
				       r.total_days, r.status, r.created_at, r.decided_at
				FROM leave_request r
				JOIN employee e ON e.id = r.employee_id
				JOIN department dep ON dep.id = e.department_id
				JOIN leave_type t ON t.id = r.leave_type_id
				""" + historyWhere(criteria) + """
				ORDER BY r.start_date DESC, r.created_at DESC, r.id
				LIMIT :limit OFFSET :offset
				""", params, (rs, row) -> new LeaveHistoryRow(uuid(rs, "id"), uuid(rs, "employee_id"),
						rs.getString("employee_code"), rs.getString("employee_name"), rs.getString("department_name"),
						new LeaveTypeRef(uuid(rs, "type_id"), rs.getString("code"), rs.getString("name"),
								rs.getString("color")),
						rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
						rs.getBigDecimal("total_days"), LeaveStatus.valueOf(rs.getString("status")),
						instant(rs, "created_at"), instant(rs, "decided_at")));
	}

	long countHistory(ReportScope scope, ReportCriteria criteria) {
		Long count = jdbc.queryForObject(ReportScope.CTE + """
				SELECT count(*) FROM leave_request r JOIN employee e ON e.id = r.employee_id
				""" + historyWhere(criteria), historyParams(scope, criteria), Long.class);
		return count == null ? 0 : count;
	}

	/** Current employees in scope per department. */
	List<DepartmentHeadcount> headcountByDepartment(ReportScope scope, UUID departmentId) {
		return jdbc.query(ReportScope.CTE + """
				SELECT dep.id, dep.name, count(e.id) AS headcount
				FROM employee e JOIN department dep ON dep.id = e.department_id
				WHERE e.employment_status <> 'EXITED' AND e.id %s
				  AND (CAST(:departmentId AS uuid) IS NULL OR dep.id = CAST(:departmentId AS uuid))
				GROUP BY dep.id, dep.name
				""".formatted(IN_SCOPE), params(scope).addValue("departmentId", departmentId),
				(rs, row) -> new DepartmentHeadcount(uuid(rs, "id"), rs.getString("name"), rs.getInt("headcount")));
	}

	/** Days taken per department (of the employee now) and leave type. */
	List<DepartmentTypeDays> daysByDepartmentAndType(ReportScope scope, LocalDate from, LocalDate to,
			UUID departmentId) {
		return jdbc.query(ReportScope.CTE + """
				SELECT dep.id AS department_id, dep.name AS department_name, t.id, t.code, t.name, t.color,
				       SUM(d.amount) AS days
				FROM leave_request r
				JOIN leave_request_day d ON d.leave_request_id = r.id
				JOIN employee e ON e.id = r.employee_id
				JOIN department dep ON dep.id = e.department_id
				JOIN leave_type t ON t.id = r.leave_type_id
				WHERE %s AND d.leave_date BETWEEN :from AND :to AND r.employee_id %s
				  AND (CAST(:departmentId AS uuid) IS NULL OR dep.id = CAST(:departmentId AS uuid))
				GROUP BY dep.id, dep.name, t.id, t.code, t.name, t.color, t.sort_order
				ORDER BY t.sort_order, t.name
				""".formatted(TAKEN, IN_SCOPE),
				params(scope).addValue("from", from).addValue("to", to).addValue("departmentId", departmentId),
				(rs, row) -> new DepartmentTypeDays(uuid(rs, "department_id"), rs.getString("department_name"),
						leaveType(rs), rs.getBigDecimal("days")));
	}

	/** Every leave type with the approved requests, employees and days taken between the dates. */
	List<TypeUsage> leaveTypeUsage(ReportScope scope, LocalDate from, LocalDate to) {
		return jdbc.query(ReportScope.CTE + """
				, taken AS (
				    SELECT r.leave_type_id, r.id AS request_id, r.employee_id, d.amount
				    FROM leave_request r JOIN leave_request_day d ON d.leave_request_id = r.id
				    WHERE %s AND d.leave_date BETWEEN :from AND :to AND r.employee_id %s
				)
				SELECT t.id, t.code, t.name, t.color, count(DISTINCT k.request_id) AS requests,
				       count(DISTINCT k.employee_id) AS employees, COALESCE(SUM(k.amount), 0) AS days
				FROM leave_type t LEFT JOIN taken k ON k.leave_type_id = t.id
				WHERE t.active OR k.request_id IS NOT NULL
				GROUP BY t.id, t.code, t.name, t.color, t.sort_order
				ORDER BY t.sort_order, t.name
				""".formatted(TAKEN, IN_SCOPE), params(scope).addValue("from", from).addValue("to", to),
				(rs, row) -> new TypeUsage(leaveType(rs), rs.getInt("requests"), rs.getInt("employees"),
						rs.getBigDecimal("days")));
	}

	/** Outcomes of the requests submitted between the dates (in the zone), per leave type. */
	List<TypeDecisions> decisions(ReportScope scope, LocalDate from, LocalDate to, ZoneId zone) {
		return jdbc.query(ReportScope.CTE + """
				SELECT t.id, t.code, t.name, t.color, count(*) AS submitted,
				       count(*) FILTER (WHERE o.approved) AS approved,
				       count(*) FILTER (WHERE o.rejected) AS rejected,
				       count(*) FILTER (WHERE r.status = 'WITHDRAWN') AS withdrawn,
				       count(*) FILTER (WHERE r.status = 'CANCELLED') AS cancelled,
				       count(*) FILTER (WHERE r.status = 'PENDING') AS pending,
				       SUM(extract(EPOCH FROM r.decided_at - r.created_at)) FILTER (WHERE o.approved OR o.rejected)
				           AS decision_seconds
				FROM leave_request r
				JOIN leave_type t ON t.id = r.leave_type_id
				CROSS JOIN LATERAL (
				    SELECT COALESCE(bool_or(h.action = 'APPROVE'), false) AS approved,
				           COALESCE(bool_or(h.action = 'REJECT'), false) AS rejected
				    FROM leave_request_history h WHERE h.leave_request_id = r.id
				) o
				WHERE (r.created_at AT TIME ZONE :zone)::date BETWEEN :from AND :to AND r.employee_id %s
				GROUP BY t.id, t.code, t.name, t.color, t.sort_order
				ORDER BY t.sort_order, t.name
				""".formatted(IN_SCOPE),
				params(scope).addValue("from", from).addValue("to", to).addValue("zone", zone.getId()),
				(rs, row) -> new TypeDecisions(leaveType(rs), rs.getInt("submitted"), rs.getInt("approved"),
						rs.getInt("rejected"), rs.getInt("withdrawn"), rs.getInt("cancelled"), rs.getInt("pending"),
						rs.getBigDecimal("decision_seconds")));
	}

	List<BalanceRow> balances(ReportScope scope, ReportCriteria criteria, int limit, long offset) {
		return jdbc.query(ReportScope.CTE + """
				SELECT e.id AS employee_id, e.employee_code, e.first_name || ' ' || e.last_name AS employee_name,
				       dep.name AS department_name, t.id, t.code, t.name, t.color, b.allocated, b.carried_forward,
				       b.adjusted, b.expired, b.carried_out, b.used, b.pending, b.available
				""" + balancesFrom() + """
				ORDER BY e.first_name, e.last_name, e.id, t.sort_order, t.name
				LIMIT :limit OFFSET :offset
				""", balanceParams(scope, criteria).addValue("limit", limit).addValue("offset", offset),
				(rs, row) -> new BalanceRow(uuid(rs, "employee_id"), rs.getString("employee_code"),
						rs.getString("employee_name"), rs.getString("department_name"), leaveType(rs),
						rs.getBigDecimal("allocated"), rs.getBigDecimal("carried_forward"), rs.getBigDecimal("adjusted"),
						rs.getBigDecimal("expired"), rs.getBigDecimal("carried_out"), rs.getBigDecimal("used"),
						rs.getBigDecimal("pending"), rs.getBigDecimal("available")));
	}

	long countBalances(ReportScope scope, ReportCriteria criteria) {
		Long count = jdbc.queryForObject(ReportScope.CTE + "SELECT count(*) " + balancesFrom(),
				balanceParams(scope, criteria), Long.class);
		return count == null ? 0 : count;
	}

	DashboardSummaryResponse.Team team(ReportScope scope, LocalDate today) {
		return jdbc.queryForObject(ReportScope.CTE + """
				, time_off AS (
				    SELECT r.employee_id, d.leave_date
				    FROM leave_request r
				    JOIN leave_request_day d ON d.leave_request_id = r.id
				    JOIN leave_type t ON t.id = r.leave_type_id
				    WHERE %s AND t.time_off AND d.leave_date BETWEEN :today AND :today + 7
				      AND r.employee_id %s
				)
				SELECT (SELECT count(*) FROM employee e WHERE e.employment_status <> 'EXITED' AND e.id %s) AS headcount,
				       (SELECT count(DISTINCT employee_id) FROM time_off WHERE leave_date = :today) AS today,
				       (SELECT count(DISTINCT employee_id) FROM time_off WHERE leave_date > :today) AS week,
				       (SELECT count(*) FROM leave_request r WHERE r.status = 'PENDING' AND r.employee_id %s) AS pending
				""".formatted(TAKEN, IN_SCOPE, IN_SCOPE, IN_SCOPE), params(scope).addValue("today", today),
				(rs, row) -> new DashboardSummaryResponse.Team(scope.name(), rs.getInt("headcount"), rs.getInt("today"),
						rs.getInt("week"), rs.getInt("pending")));
	}

	private static String historyWhere(ReportCriteria criteria) {
		StringBuilder where = new StringBuilder("WHERE r.start_date <= :to AND r.end_date >= :from AND r.employee_id ")
				.append(IN_SCOPE);
		if (criteria.employeeId() != null) {
			where.append(" AND r.employee_id = :employeeId");
		}
		if (criteria.departmentId() != null) {
			where.append(" AND e.department_id = :departmentId");
		}
		if (criteria.leaveTypeId() != null) {
			where.append(" AND r.leave_type_id = :leaveTypeId");
		}
		if (criteria.status() != null) {
			where.append(" AND r.status = :status");
		}
		return where.append('\n').toString();
	}

	private static MapSqlParameterSource historyParams(ReportScope scope, ReportCriteria criteria) {
		return params(scope).addValue("from", criteria.from()).addValue("to", criteria.to())
				.addValue("employeeId", criteria.employeeId()).addValue("departmentId", criteria.departmentId())
				.addValue("leaveTypeId", criteria.leaveTypeId())
				.addValue("status", criteria.status() == null ? null : criteria.status().name());
	}

	private static String balancesFrom() {
		return """
				FROM leave_balance b
				JOIN employee e ON e.id = b.employee_id
				JOIN department dep ON dep.id = e.department_id
				JOIN leave_type t ON t.id = b.leave_type_id
				WHERE b.leave_period_id = :leavePeriodId AND e.employment_status <> 'EXITED' AND e.id %s
				  AND (CAST(:departmentId AS uuid) IS NULL OR e.department_id = CAST(:departmentId AS uuid))
				  AND (CAST(:leaveTypeId AS uuid) IS NULL OR b.leave_type_id = CAST(:leaveTypeId AS uuid))
				  AND (CAST(:employeeId AS uuid) IS NULL OR e.id = CAST(:employeeId AS uuid))
				""".formatted(IN_SCOPE);
	}

	private static MapSqlParameterSource balanceParams(ReportScope scope, ReportCriteria criteria) {
		return params(scope).addValue("leavePeriodId", criteria.leavePeriodId())
				.addValue("departmentId", criteria.departmentId()).addValue("leaveTypeId", criteria.leaveTypeId())
				.addValue("employeeId", criteria.employeeId());
	}

	private static MapSqlParameterSource params(ReportScope scope) {
		return new MapSqlParameterSource().addValue("tenantWide", scope.tenantWide())
				.addValue("scopeManager", scope.managerEmployeeId());
	}

	private static LeaveTypeRef leaveType(ResultSet rs) throws SQLException {
		return new LeaveTypeRef(uuid(rs, "id"), rs.getString("code"), rs.getString("name"), rs.getString("color"));
	}

	private static UUID uuid(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, UUID.class);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

	record MonthTypeDays(String month, LeaveTypeRef leaveType, BigDecimal days) {
	}

	record MonthActionCount(String month, String action, int count) {
	}

	record DepartmentHeadcount(UUID departmentId, String departmentName, int headcount) {
	}

	record DepartmentTypeDays(UUID departmentId, String departmentName, LeaveTypeRef leaveType, BigDecimal days) {
	}

	record TypeUsage(LeaveTypeRef leaveType, int requests, int employees, BigDecimal days) {
	}

	/** @param decisionSeconds summed over the decided requests; null when none was decided */
	record TypeDecisions(LeaveTypeRef leaveType, int submitted, int approved, int rejected, int withdrawn,
			int cancelled, int pending, BigDecimal decisionSeconds) {
	}

}
