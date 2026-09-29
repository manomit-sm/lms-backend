package com.bsolz.lms.balance.service;

import com.bsolz.lms.balance.api.BalanceApi;
import com.bsolz.lms.balance.entity.LeaveBalance;
import com.bsolz.lms.balance.exception.BalanceErrorCode;
import com.bsolz.lms.balance.mapper.BalanceMapper;
import com.bsolz.lms.balance.repository.LeaveBalanceRepository;
import com.bsolz.lms.balance.repository.LeaveBalanceTransactionRepository;
import com.bsolz.lms.balance.web.dto.AdjustmentRequest;
import com.bsolz.lms.balance.web.dto.BalanceResponse;
import com.bsolz.lms.balance.web.dto.BalanceTransactionResponse;
import com.bsolz.lms.leavepolicy.api.LeavePeriodInfo;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.leavepolicy.api.LeaveTypeInfo;
import com.bsolz.lms.organization.api.EmployeeVisibility;
import com.bsolz.lms.settings.api.SettingsApi;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.security.CurrentUser;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Balance views and HR adjustments for the REST API, limited to employees the caller can see. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BalanceQueryService {

	private final LeaveBalanceRepository balanceRepository;

	private final LeaveBalanceTransactionRepository transactionRepository;

	private final BalanceApi balanceApi;

	private final LeavePolicyApi policyApi;

	private final EmployeeVisibility employeeVisibility;

	private final SettingsApi settings;

	private final BalanceMapper mapper;

	/** An employee's (default: the current user's) balances for a period (default: the current one). */
	public List<BalanceResponse> list(UUID employeeId, UUID leavePeriodId) {
		UUID target = employeeId != null ? employeeId : currentEmployeeId();
		requireVisible(target);
		LeavePeriodInfo period = period(leavePeriodId);
		List<LeaveTypeInfo> activeTypes = policyApi.findActiveLeaveTypes();
		Map<UUID, LeaveTypeInfo> types = new HashMap<>();
		activeTypes.forEach(type -> types.put(type.id(), type));
		List<LeaveBalance> balances = balanceRepository.findAllByEmployeeIdAndLeavePeriodId(target, period.id());
		balances.forEach(balance -> types.computeIfAbsent(balance.getLeaveTypeId(),
				id -> policyApi.findLeaveType(id).orElseThrow()));
		// Leave types in display order; inactive ones (still holding balances) last.
		Comparator<LeaveBalance> order = Comparator.comparingInt(balance -> {
			int index = activeTypes.indexOf(types.get(balance.getLeaveTypeId()));
			return index < 0 ? Integer.MAX_VALUE : index;
		});
		return balances.stream().sorted(order)
				.map(balance -> mapper.toResponse(balance, types.get(balance.getLeaveTypeId()), period))
				.toList();
	}

	/** A balance's ledger, newest first. */
	public Page<BalanceTransactionResponse> transactions(UUID balanceId, Pageable pageable) {
		LeaveBalance balance = balanceRepository.findById(balanceId)
				.orElseThrow(() -> new ApiException(BalanceErrorCode.BALANCE_NOT_FOUND, "Balance not found"));
		requireVisible(balance.getEmployeeId());
		return transactionRepository.findAllByLeaveBalanceIdOrderBySeqDesc(balanceId, pageable).map(mapper::toResponse);
	}

	@Transactional
	public BalanceResponse adjust(AdjustmentRequest request) {
		requireVisible(request.employeeId());
		LeavePeriodInfo period = period(request.leavePeriodId());
		balanceApi.adjust(request.employeeId(), request.leaveTypeId(), period.id(), request.amount(), request.reason());
		balanceRepository.flush();
		LeaveBalance balance = balanceRepository
				.findByEmployeeIdAndLeaveTypeIdAndLeavePeriodId(request.employeeId(), request.leaveTypeId(), period.id())
				.orElseThrow();
		return mapper.toResponse(balance, policyApi.findLeaveType(request.leaveTypeId()).orElseThrow(), period);
	}

	private LeavePeriodInfo period(UUID leavePeriodId) {
		if (leavePeriodId != null) {
			return policyApi.findPeriod(leavePeriodId)
					.orElseThrow(() -> new ApiException(BalanceErrorCode.LEAVE_PERIOD_NOT_FOUND, "Leave period not found"));
		}
		return policyApi.findPeriodContaining(settings.today())
				.orElseThrow(() -> new ApiException(BalanceErrorCode.NO_LEAVE_PERIOD, "No leave period covers today"));
	}

	private void requireVisible(UUID employeeId) {
		if (!employeeVisibility.canView(employeeId)) {
			throw new AccessDeniedException("Employee not visible");
		}
	}

	private static UUID currentEmployeeId() {
		UUID employeeId = CurrentUser.require().employeeId();
		if (employeeId == null) {
			throw new ApiException(BalanceErrorCode.EMPLOYEE_NOT_FOUND, "Your user is not linked to an employee record");
		}
		return employeeId;
	}

}
