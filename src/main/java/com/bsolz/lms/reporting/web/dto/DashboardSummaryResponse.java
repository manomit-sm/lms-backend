package com.bsolz.lms.reporting.web.dto;

import com.bsolz.lms.leave.model.enums.LeaveStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The caller's dashboard; each section is present only when it applies to them.
 *
 * @param me their own balances and leave; null when their user isn't linked to an employee
 * @param approvals what waits for their decision
 * @param team their reporting line (REPORT_VIEW_TEAM) or the whole organisation (REPORT_VIEW_ALL); null
 * without either permission
 */
public record DashboardSummaryResponse(LocalDate today, Me me, Approvals approvals, Team team) {

	/**
	 * @param balances in the current leave period
	 * @param upcomingLeave pending or approved leave that hasn't ended, soonest first (at most 5)
	 */
	public record Me(UUID employeeId, List<Balance> balances, List<UpcomingLeave> upcomingLeave,
			int pendingRequests) {
	}

	public record Balance(LeaveTypeRef leaveType, BigDecimal available, BigDecimal used, BigDecimal pending) {
	}

	public record UpcomingLeave(UUID requestId, LeaveTypeRef leaveType, LocalDate startDate, LocalDate endDate,
			BigDecimal totalDays, LeaveStatus status) {
	}

	public record Approvals(int pendingTasks) {
	}

	/**
	 * @param scope {@code TEAM} or {@code TENANT}
	 * @param headcount current employees
	 * @param onLeaveToday employees on approved time off today
	 * @param onLeaveNextSevenDays employees with approved time off in the next seven days
	 * @param pendingRequests requests awaiting approval
	 */
	public record Team(String scope, int headcount, int onLeaveToday, int onLeaveNextSevenDays,
			int pendingRequests) {
	}

}
