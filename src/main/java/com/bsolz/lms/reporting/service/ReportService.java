package com.bsolz.lms.reporting.service;

import com.bsolz.lms.leave.model.enums.LeaveStatus;
import com.bsolz.lms.leavepolicy.api.LeavePeriodInfo;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.reporting.domain.ReportScope;
import com.bsolz.lms.reporting.exception.ReportErrorCode;
import com.bsolz.lms.reporting.web.dto.BalanceRow;
import com.bsolz.lms.reporting.web.dto.DecisionsResponse;
import com.bsolz.lms.reporting.web.dto.DepartmentUsageResponse;
import com.bsolz.lms.reporting.web.dto.LeaveHistoryRow;
import com.bsolz.lms.reporting.web.dto.LeaveTypeDays;
import com.bsolz.lms.reporting.web.dto.LeaveTypeUsageResponse;
import com.bsolz.lms.reporting.web.dto.MonthlyStatisticsResponse;
import com.bsolz.lms.settings.api.SettingsApi;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.security.CurrentUser;
import com.bsolz.lms.shared.security.LmsPrincipal;
import com.bsolz.lms.shared.security.Permissions;
import com.bsolz.lms.shared.web.PageResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports for the current user, scoped by their permissions: REPORT_VIEW_ALL covers the whole tenant,
 * REPORT_VIEW_TEAM their reporting line (and themselves). Dates default to the current leave period.
 * The {@code (scope, criteria)} variants serve exports, which run later without the user.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportService {

	/** Longest date range of one report: two years. */
	static final int MAX_RANGE_DAYS = 731;

	private final ReportQueries queries;

	private final LeavePolicyApi policyApi;

	private final OrganizationApi organizationApi;

	private final SettingsApi settings;

	/** The current user's report scope. */
	public ReportScope currentScope() {
		LmsPrincipal user = CurrentUser.require();
		if (user.permissions().contains(Permissions.REPORT_VIEW_ALL)) {
			return ReportScope.tenant();
		}
		if (user.employeeId() == null) {
			throw new ApiException(ReportErrorCode.NOT_LINKED_TO_EMPLOYEE,
					"Team reports need your user to be linked to an employee record");
		}
		return ReportScope.reportingLine(user.employeeId());
	}

	/**
	 * Fills in default dates (the current leave period; for balances the current period too) and checks the
	 * range and that a requested employee is in scope.
	 */
	public ReportCriteria resolve(ReportScope scope, LocalDate from, LocalDate to, UUID employeeId, UUID departmentId,
			UUID leaveTypeId, UUID leavePeriodId, LeaveStatus status) {
		LocalDate start = from;
		LocalDate end = to;
		UUID period = leavePeriodId;
		if (start == null || end == null || period == null) {
			LeavePeriodInfo current = policyApi.findPeriodContaining(settings.today()).orElseThrow(
					() -> new ApiException(ReportErrorCode.NO_LEAVE_PERIOD, "No leave period covers today"));
			start = start != null ? start : current.startDate();
			end = end != null ? end : current.endDate();
			period = period != null ? period : current.id();
		}
		if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
			throw new ApiException(ReportErrorCode.INVALID_RANGE,
					"to must not be before from, and a report covers at most two years");
		}
		if (employeeId != null && !scope.tenantWide() && !employeeId.equals(scope.managerEmployeeId())
				&& !organizationApi.isInReportingLine(scope.managerEmployeeId(), employeeId)) {
			throw new ApiException(ReportErrorCode.EMPLOYEE_NOT_VISIBLE, "That employee is not in your reporting line");
		}
		return new ReportCriteria(start, end, employeeId, departmentId, leaveTypeId, period, status);
	}

	public MonthlyStatisticsResponse monthlyStatistics(ReportScope scope, ReportCriteria criteria) {
		Map<String, List<LeaveTypeDays>> days = new LinkedHashMap<>();
		queries.daysByMonthAndType(scope, criteria.from(), criteria.to()).forEach(row -> days
				.computeIfAbsent(row.month(), month -> new ArrayList<>()).add(new LeaveTypeDays(row.leaveType(), row.days())));
		Map<String, Map<String, Integer>> actions = new LinkedHashMap<>();
		queries.actionsByMonth(scope, criteria.from(), criteria.to(), settings.current().timezone()).forEach(row -> actions
				.computeIfAbsent(row.month(), month -> new LinkedHashMap<>()).put(row.action(), row.count()));

		List<MonthlyStatisticsResponse.Month> months = new ArrayList<>();
		for (YearMonth month = YearMonth.from(criteria.from()); !month.isAfter(YearMonth.from(criteria.to()));
				month = month.plusMonths(1)) {
			String key = month.toString();
			List<LeaveTypeDays> byType = days.getOrDefault(key, List.of());
			Map<String, Integer> counts = actions.getOrDefault(key, Map.of());
			months.add(new MonthlyStatisticsResponse.Month(key, sum(byType), byType, counts.getOrDefault("SUBMIT", 0),
					counts.getOrDefault("APPROVE", 0), counts.getOrDefault("REJECT", 0)));
		}
		return new MonthlyStatisticsResponse(criteria.from(), criteria.to(), scope.name(), months);
	}

	public PageResponse<LeaveHistoryRow> leaveHistory(ReportScope scope, ReportCriteria criteria, Pageable pageable) {
		long total = queries.countHistory(scope, criteria);
		List<LeaveHistoryRow> rows = queries.history(scope, criteria, pageable.getPageSize(), pageable.getOffset());
		return page(rows, pageable, total);
	}

	public List<LeaveHistoryRow> leaveHistory(ReportScope scope, ReportCriteria criteria, int limit) {
		return queries.history(scope, criteria, limit, 0);
	}

	public DepartmentUsageResponse departmentUsage(ReportScope scope, ReportCriteria criteria) {
		Map<UUID, String> names = new LinkedHashMap<>();
		Map<UUID, Integer> headcounts = new LinkedHashMap<>();
		queries.headcountByDepartment(scope, criteria.departmentId()).forEach(row -> {
			names.put(row.departmentId(), row.departmentName());
			headcounts.put(row.departmentId(), row.headcount());
		});
		Map<UUID, List<LeaveTypeDays>> days = new LinkedHashMap<>();
		queries.daysByDepartmentAndType(scope, criteria.from(), criteria.to(), criteria.departmentId()).forEach(row -> {
			names.putIfAbsent(row.departmentId(), row.departmentName());
			days.computeIfAbsent(row.departmentId(), id -> new ArrayList<>())
					.add(new LeaveTypeDays(row.leaveType(), row.days()));
		});
		List<DepartmentUsageResponse.Department> departments = names.entrySet().stream().map(entry -> {
			int headcount = headcounts.getOrDefault(entry.getKey(), 0);
			List<LeaveTypeDays> byType = days.getOrDefault(entry.getKey(), List.of());
			BigDecimal taken = sum(byType);
			return new DepartmentUsageResponse.Department(entry.getKey(), entry.getValue(), headcount, taken,
					headcount == 0 ? null : taken.divide(BigDecimal.valueOf(headcount), 2, RoundingMode.HALF_UP),
					byType);
		}).sorted((a, b) -> a.departmentName().compareToIgnoreCase(b.departmentName())).toList();
		return new DepartmentUsageResponse(criteria.from(), criteria.to(), scope.name(), departments);
	}

	public LeaveTypeUsageResponse leaveTypeUsage(ReportScope scope, ReportCriteria criteria) {
		return new LeaveTypeUsageResponse(criteria.from(), criteria.to(), scope.name(),
				queries.leaveTypeUsage(scope, criteria.from(), criteria.to()).stream()
						.map(row -> new LeaveTypeUsageResponse.LeaveType(row.leaveType(), row.requests(),
								row.employees(), row.days()))
						.toList());
	}

	public DecisionsResponse decisions(ReportScope scope, ReportCriteria criteria) {
		List<ReportQueries.TypeDecisions> rows = queries.decisions(scope, criteria.from(), criteria.to(),
				settings.current().timezone());
		ReportQueries.TypeDecisions total = new ReportQueries.TypeDecisions(null,
				rows.stream().mapToInt(ReportQueries.TypeDecisions::submitted).sum(),
				rows.stream().mapToInt(ReportQueries.TypeDecisions::approved).sum(),
				rows.stream().mapToInt(ReportQueries.TypeDecisions::rejected).sum(),
				rows.stream().mapToInt(ReportQueries.TypeDecisions::withdrawn).sum(),
				rows.stream().mapToInt(ReportQueries.TypeDecisions::cancelled).sum(),
				rows.stream().mapToInt(ReportQueries.TypeDecisions::pending).sum(),
				rows.stream().map(ReportQueries.TypeDecisions::decisionSeconds).filter(Objects::nonNull)
						.reduce(BigDecimal::add).orElse(null));
		return new DecisionsResponse(criteria.from(), criteria.to(), scope.name(), stats(total),
				rows.stream().map(ReportService::stats).toList());
	}

	public PageResponse<BalanceRow> balances(ReportScope scope, ReportCriteria criteria, Pageable pageable) {
		long total = queries.countBalances(scope, criteria);
		return page(queries.balances(scope, criteria, pageable.getPageSize(), pageable.getOffset()), pageable, total);
	}

	public List<BalanceRow> balances(ReportScope scope, ReportCriteria criteria, int limit) {
		return queries.balances(scope, criteria, limit, 0);
	}

	private static DecisionsResponse.Stats stats(ReportQueries.TypeDecisions row) {
		int decided = row.approved() + row.rejected();
		return new DecisionsResponse.Stats(row.leaveType(), row.submitted(), row.approved(), row.rejected(),
				row.withdrawn(), row.cancelled(), row.pending(),
				decided == 0 ? null
						: BigDecimal.valueOf(row.approved()).divide(BigDecimal.valueOf(decided), 2, RoundingMode.HALF_UP),
				decided == 0 || row.decisionSeconds() == null ? null
						: row.decisionSeconds().divide(BigDecimal.valueOf(3600L * decided), 1, RoundingMode.HALF_UP));
	}

	private static BigDecimal sum(List<LeaveTypeDays> days) {
		return days.stream().map(LeaveTypeDays::days).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static <T> PageResponse<T> page(List<T> rows, Pageable pageable, long total) {
		int size = pageable.getPageSize();
		return new PageResponse<>(rows, pageable.getPageNumber(), size, total, (int) ((total + size - 1) / size));
	}

}
