package com.bsolz.lms.reporting.service;

import com.bsolz.lms.approval.api.ApprovalApi;
import com.bsolz.lms.balance.api.BalanceApi;
import com.bsolz.lms.balance.api.BalanceSnapshot;
import com.bsolz.lms.leave.api.LeaveApi;
import com.bsolz.lms.leave.api.LeaveSummary;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.leavepolicy.api.LeaveTypeInfo;
import com.bsolz.lms.reporting.domain.ReportScope;
import com.bsolz.lms.reporting.web.dto.DashboardSummaryResponse;
import com.bsolz.lms.reporting.web.dto.LeaveTypeRef;
import com.bsolz.lms.settings.api.SettingsApi;
import com.bsolz.lms.shared.security.CurrentUser;
import com.bsolz.lms.shared.security.LmsPrincipal;
import com.bsolz.lms.shared.security.Permissions;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The role-aware dashboard: one call for everything the home screen shows. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {

	static final int UPCOMING_LIMIT = 5;

	private final ReportQueries queries;

	private final BalanceApi balanceApi;

	private final LeaveApi leaveApi;

	private final LeavePolicyApi policyApi;

	private final ApprovalApi approvalApi;

	private final SettingsApi settings;

	public DashboardSummaryResponse summary() {
		LmsPrincipal user = CurrentUser.require();
		LocalDate today = settings.today();
		DashboardSummaryResponse.Me me = user.employeeId() == null ? null : me(user.employeeId(), today);
		DashboardSummaryResponse.Approvals approvals = user.userId() == null ? null
				: new DashboardSummaryResponse.Approvals(approvalApi.findPendingTasks(user.userId()).size());
		DashboardSummaryResponse.Team team = null;
		if (user.permissions().contains(Permissions.REPORT_VIEW_ALL)) {
			team = queries.team(ReportScope.tenant(), today);
		}
		else if (user.permissions().contains(Permissions.REPORT_VIEW_TEAM) && user.employeeId() != null) {
			team = queries.team(ReportScope.reportingLine(user.employeeId()), today);
		}
		return new DashboardSummaryResponse(today, me, approvals, team);
	}

	private DashboardSummaryResponse.Me me(UUID employeeId, LocalDate today) {
		Map<UUID, BalanceSnapshot> byType = new HashMap<>();
		policyApi.findPeriodContaining(today).map(period -> balanceApi.findForEmployee(employeeId, period.id()))
				.orElse(List.of()).forEach(balance -> byType.put(balance.leaveTypeId(), balance));
		// Active leave types, in display order
		List<DashboardSummaryResponse.Balance> balances = policyApi.findActiveLeaveTypes().stream()
				.filter(type -> byType.containsKey(type.id()))
				.map(type -> new DashboardSummaryResponse.Balance(ref(type), byType.get(type.id()).available(),
						byType.get(type.id()).used(), byType.get(type.id()).pending()))
				.toList();
		Map<UUID, Optional<LeaveTypeInfo>> types = new HashMap<>();
		List<LeaveSummary> live = leaveApi.findLeaves(Set.of(employeeId), today.minusYears(1), today.plusYears(1),
				Set.of(LeaveStatus.PENDING, LeaveStatus.APPROVED, LeaveStatus.CANCELLATION_PENDING));
		List<DashboardSummaryResponse.UpcomingLeave> upcoming = live.stream()
				.filter(leave -> !leave.endDate().isBefore(today))
				.sorted(Comparator.comparing(LeaveSummary::startDate))
				.limit(UPCOMING_LIMIT)
				.map(leave -> new DashboardSummaryResponse.UpcomingLeave(leave.id(),
						types.computeIfAbsent(leave.leaveTypeId(), policyApi::findLeaveType).map(DashboardService::ref)
								.orElse(null),
						leave.startDate(), leave.endDate(), leave.totalDays(), leave.status()))
				.toList();
		int pending = (int) live.stream().filter(leave -> leave.status() == LeaveStatus.PENDING).count();
		return new DashboardSummaryResponse.Me(employeeId, balances, upcoming, pending);
	}

	private static LeaveTypeRef ref(LeaveTypeInfo type) {
		return new LeaveTypeRef(type.id(), type.code(), type.name(), type.color());
	}

}
