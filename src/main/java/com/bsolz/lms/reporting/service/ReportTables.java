package com.bsolz.lms.reporting.service;

import com.bsolz.lms.reporting.domain.ReportScope;
import com.bsolz.lms.reporting.domain.ReportTable;
import com.bsolz.lms.reporting.exception.ReportErrorCode;
import com.bsolz.lms.reporting.model.enums.ReportType;
import com.bsolz.lms.reporting.web.dto.BalanceRow;
import com.bsolz.lms.reporting.web.dto.DecisionsResponse;
import com.bsolz.lms.reporting.web.dto.DepartmentUsageResponse;
import com.bsolz.lms.reporting.web.dto.LeaveHistoryRow;
import com.bsolz.lms.reporting.web.dto.LeaveTypeDays;
import com.bsolz.lms.reporting.web.dto.LeaveTypeRef;
import com.bsolz.lms.reporting.web.dto.LeaveTypeUsageResponse;
import com.bsolz.lms.reporting.web.dto.MonthlyStatisticsResponse;
import com.bsolz.lms.shared.exception.ApiException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Every report as a table, for export. Paged reports are exported whole, up to {@value #MAX_ROWS} rows. */
@Component
@RequiredArgsConstructor
class ReportTables {

	static final int MAX_ROWS = 100_000;

	private final ReportService reports;

	ReportTable build(ReportType type, ReportScope scope, ReportCriteria criteria) {
		return switch (type) {
			case MONTHLY_STATISTICS -> monthlyStatistics(reports.monthlyStatistics(scope, criteria));
			case EMPLOYEE_LEAVE_HISTORY -> history(capped(reports.leaveHistory(scope, criteria, MAX_ROWS + 1)));
			case DEPARTMENT_USAGE -> departmentUsage(reports.departmentUsage(scope, criteria));
			case LEAVE_TYPE_USAGE -> leaveTypeUsage(reports.leaveTypeUsage(scope, criteria));
			case DECISIONS -> decisions(reports.decisions(scope, criteria));
			case BALANCES -> balances(capped(reports.balances(scope, criteria, MAX_ROWS + 1)));
		};
	}

	private static ReportTable monthlyStatistics(MonthlyStatisticsResponse report) {
		List<LeaveTypeRef> types = distinctTypes(report.months().stream().flatMap(month -> month.byLeaveType().stream()));
		List<String> headers = new ArrayList<>(List.of("Month", "Days taken", "Submitted", "Approved", "Rejected"));
		types.forEach(type -> headers.add(type.name() + " (days)"));
		List<List<Object>> rows = report.months().stream().map(month -> {
			List<Object> row = new ArrayList<>(List.of(month.month(), month.daysTaken(), month.submitted(),
					month.approved(), month.rejected()));
			row.addAll(daysPerType(types, month.byLeaveType()));
			return row;
		}).toList();
		return new ReportTable("Monthly statistics", headers, rows);
	}

	private static ReportTable history(List<LeaveHistoryRow> rows) {
		return new ReportTable("Leave history", List.of("Employee code", "Employee", "Department", "Leave type",
				"Start date", "End date", "Days", "Status", "Submitted at", "Decided at"),
				rows.stream().map(row -> Arrays.<Object>asList(row.employeeCode(), row.employeeName(),
						row.departmentName(), row.leaveType().name(), row.startDate(), row.endDate(), row.totalDays(),
						row.status().name(), row.submittedAt(), row.decidedAt())).toList());
	}

	private static ReportTable departmentUsage(DepartmentUsageResponse report) {
		List<LeaveTypeRef> types = distinctTypes(
				report.departments().stream().flatMap(department -> department.byLeaveType().stream()));
		List<String> headers = new ArrayList<>(List.of("Department", "Headcount", "Days taken", "Days per employee"));
		types.forEach(type -> headers.add(type.name() + " (days)"));
		List<List<Object>> rows = report.departments().stream().map(department -> {
			List<Object> row = new ArrayList<>();
			row.add(department.departmentName());
			row.add(department.headcount());
			row.add(department.daysTaken());
			row.add(department.averageDaysPerEmployee());
			row.addAll(daysPerType(types, department.byLeaveType()));
			return row;
		}).toList();
		return new ReportTable("Department usage", headers, rows);
	}

	private static ReportTable leaveTypeUsage(LeaveTypeUsageResponse report) {
		return new ReportTable("Leave type usage", List.of("Leave type", "Requests", "Employees", "Days taken"),
				report.leaveTypes().stream().map(type -> List.<Object>of(type.leaveType().name(), type.requests(),
						type.employees(), type.daysTaken())).toList());
	}

	private static ReportTable decisions(DecisionsResponse report) {
		List<List<Object>> rows = new ArrayList<>();
		Stream.concat(report.byLeaveType().stream(), Stream.of(report.total())).forEach(stats -> rows.add(
				Arrays.asList(stats.leaveType() == null ? "Total" : stats.leaveType().name(), stats.submitted(),
						stats.approved(), stats.rejected(), stats.withdrawn(), stats.cancelled(), stats.pending(),
						stats.approvalRate(), stats.averageHoursToDecision())));
		return new ReportTable("Approvals and rejections", List.of("Leave type", "Submitted", "Approved", "Rejected",
				"Withdrawn", "Cancelled", "Pending", "Approval rate", "Average hours to decision"), rows);
	}

	private static ReportTable balances(List<BalanceRow> rows) {
		return new ReportTable("Balances", List.of("Employee code", "Employee", "Department", "Leave type", "Allocated",
				"Carried forward", "Adjusted", "Expired", "Carried out", "Used", "Pending", "Available"),
				rows.stream().map(row -> List.<Object>of(row.employeeCode(), row.employeeName(), row.departmentName(),
						row.leaveType().name(), row.allocated(), row.carriedForward(), row.adjusted(), row.expired(),
						row.carriedOut(), row.used(), row.pending(), row.available())).toList());
	}

	private static <T> List<T> capped(List<T> rows) {
		if (rows.size() > MAX_ROWS) {
			throw new ApiException(ReportErrorCode.EXPORT_TOO_LARGE,
					"The export would have more than " + MAX_ROWS + " rows; narrow the filters");
		}
		return rows;
	}

	private static List<LeaveTypeRef> distinctTypes(Stream<LeaveTypeDays> days) {
		Map<UUID, LeaveTypeRef> types = new LinkedHashMap<>();
		days.forEach(entry -> types.putIfAbsent(entry.leaveType().id(), entry.leaveType()));
		return List.copyOf(types.values());
	}

	private static List<Object> daysPerType(List<LeaveTypeRef> types, List<LeaveTypeDays> days) {
		return types.stream().map(type -> (Object) days.stream().filter(entry -> entry.leaveType().id().equals(type.id()))
				.map(LeaveTypeDays::days).findFirst().orElse(BigDecimal.ZERO)).toList();
	}

}
