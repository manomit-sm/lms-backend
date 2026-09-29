package com.bsolz.lms.reporting.web;

import com.bsolz.lms.leave.model.enums.LeaveStatus;
import com.bsolz.lms.reporting.domain.ReportScope;
import com.bsolz.lms.reporting.service.ReportCriteria;
import com.bsolz.lms.reporting.service.ReportService;
import com.bsolz.lms.reporting.web.dto.BalanceRow;
import com.bsolz.lms.reporting.web.dto.DecisionsResponse;
import com.bsolz.lms.reporting.web.dto.DepartmentUsageResponse;
import com.bsolz.lms.reporting.web.dto.LeaveHistoryRow;
import com.bsolz.lms.reporting.web.dto.LeaveTypeUsageResponse;
import com.bsolz.lms.reporting.web.dto.MonthlyStatisticsResponse;
import com.bsolz.lms.shared.web.PageResponse;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports, for REPORT_VIEW_ALL (the whole organisation) or REPORT_VIEW_TEAM (the caller's reporting
 * line). {@code from}/{@code to} are inclusive, default to the current leave period and span at most two
 * years. Every report can also be exported: see {@code /api/v1/reports/exports}.
 */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyAuthority('REPORT_VIEW_TEAM', 'REPORT_VIEW_ALL')")
@RequiredArgsConstructor
class ReportController {

	private final ReportService service;

	/** Leave taken and requests submitted, approved and rejected, per month. */
	@GetMapping("/monthly-statistics")
	MonthlyStatisticsResponse monthlyStatistics(@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {
		ReportScope scope = service.currentScope();
		return service.monthlyStatistics(scope, criteria(scope, from, to, null, null, null, null, null));
	}

	/** Leave requests overlapping the dates, latest first. */
	@GetMapping("/employee-leave-history")
	PageResponse<LeaveHistoryRow> employeeLeaveHistory(@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to, @RequestParam(required = false) UUID employeeId,
			@RequestParam(required = false) UUID departmentId, @RequestParam(required = false) UUID leaveTypeId,
			@RequestParam(required = false) LeaveStatus status, @PageableDefault(size = 20) Pageable pageable) {
		ReportScope scope = service.currentScope();
		return service.leaveHistory(scope,
				criteria(scope, from, to, employeeId, departmentId, leaveTypeId, null, status), unsorted(pageable));
	}

	@GetMapping("/department-usage")
	DepartmentUsageResponse departmentUsage(@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to, @RequestParam(required = false) UUID departmentId) {
		ReportScope scope = service.currentScope();
		return service.departmentUsage(scope, criteria(scope, from, to, null, departmentId, null, null, null));
	}

	@GetMapping("/leave-type-usage")
	LeaveTypeUsageResponse leaveTypeUsage(@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {
		ReportScope scope = service.currentScope();
		return service.leaveTypeUsage(scope, criteria(scope, from, to, null, null, null, null, null));
	}

	/** Approved, rejected, withdrawn, cancelled and pending requests among those submitted between the dates. */
	@GetMapping("/decisions")
	DecisionsResponse decisions(@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {
		ReportScope scope = service.currentScope();
		return service.decisions(scope, criteria(scope, from, to, null, null, null, null, null));
	}

	/** Current employees' balances in the leave period (default: the current one). */
	@GetMapping("/balances")
	PageResponse<BalanceRow> balances(@RequestParam(required = false) UUID leavePeriodId,
			@RequestParam(required = false) UUID employeeId, @RequestParam(required = false) UUID departmentId,
			@RequestParam(required = false) UUID leaveTypeId, @PageableDefault(size = 50) Pageable pageable) {
		ReportScope scope = service.currentScope();
		return service.balances(scope,
				criteria(scope, null, null, employeeId, departmentId, leaveTypeId, leavePeriodId, null),
				unsorted(pageable));
	}

	private ReportCriteria criteria(ReportScope scope, LocalDate from, LocalDate to, UUID employeeId,
			UUID departmentId, UUID leaveTypeId, UUID leavePeriodId, LeaveStatus status) {
		return service.resolve(scope, from, to, employeeId, departmentId, leaveTypeId, leavePeriodId, status);
	}

	/** Reports have a fixed order. */
	private static Pageable unsorted(Pageable pageable) {
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
	}

}
