package com.bsolz.lms.balance.service;

import com.bsolz.lms.balance.api.BalanceAdjusted;
import com.bsolz.lms.balance.api.BalanceApi;
import com.bsolz.lms.balance.api.BalanceSnapshot;
import com.bsolz.lms.balance.api.HoldRequest;
import com.bsolz.lms.balance.entity.LeaveBalance;
import com.bsolz.lms.balance.entity.LeaveBalanceTransaction;
import com.bsolz.lms.balance.exception.BalanceErrorCode;
import com.bsolz.lms.balance.mapper.BalanceMapper;
import com.bsolz.lms.balance.model.enums.BalanceReferenceType;
import com.bsolz.lms.balance.model.enums.BalanceTransactionType;
import com.bsolz.lms.balance.repository.LeaveBalanceRepository;
import com.bsolz.lms.balance.repository.LeaveBalanceTransactionRepository;
import com.bsolz.lms.leavepolicy.api.LeavePeriodInfo;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.leavepolicy.api.LeaveTypeInfo;
import com.bsolz.lms.leavepolicy.api.ResolvedPolicy;
import com.bsolz.lms.leavepolicy.model.enums.LeavePeriodStatus;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.shared.validation.HalfDaysValidator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
class BalanceApiService implements BalanceApi {

	private final BalanceLedger ledger;

	private final LeaveBalanceRepository balanceRepository;

	private final LeaveBalanceTransactionRepository transactionRepository;

	private final OrganizationApi organizationApi;

	private final LeavePolicyApi policyApi;

	private final BalanceMapper mapper;

	private final ApplicationEventPublisher events;

	@Override
	public Optional<BalanceSnapshot> hold(HoldRequest request) {
		requirePositiveHalfDays(request.days());
		LeaveTypeInfo type = requireLeaveType(request.leaveTypeId());
		if (!type.balanceTracked()) {
			return Optional.empty();
		}
		EmployeeSummary employee = requireEmployee(request.employeeId());
		LeavePeriodInfo period = policyApi.findPeriodContaining(request.leaveDate())
				.orElseThrow(() -> new ApiException(BalanceErrorCode.NO_LEAVE_PERIOD,
						"No leave period covers " + request.leaveDate()));
		requireOpen(period);
		ResolvedPolicy policy = policyApi.resolve(employee, type.id(), request.leaveDate())
				.orElseThrow(() -> new ApiException(BalanceErrorCode.NOT_ELIGIBLE,
						"The employee is not eligible for " + type.name()));

		LeaveBalance balance = ledger.lock(employee.id(), type.id(), period.id());
		if (outstanding(transactionRepository.findAllByReferenceId(request.referenceId())).held().signum() > 0) {
			throw new ApiException(BalanceErrorCode.ALREADY_HELD, "This request already holds days");
		}
		BigDecimal remaining = balance.getAvailable().subtract(request.days());
		if (remaining.compareTo(policy.negativeBalanceLimit().negate()) < 0) {
			throw new ApiException(BalanceErrorCode.INSUFFICIENT_BALANCE,
					"Not enough " + type.name() + ": " + balance.getAvailable() + " day(s) available, "
							+ request.days() + " requested",
					Map.of("available", balance.getAvailable(), "requested", request.days()));
		}
		ledger.post(balance, BalanceTransactionType.HOLD, request.days(), BalanceReferenceType.LEAVE_REQUEST,
				request.referenceId(), null);
		return Optional.of(mapper.toSnapshot(balance));
	}

	@Override
	public void release(UUID referenceId) {
		settle(referenceId, BalanceTransactionType.RELEASE);
	}

	@Override
	public void consume(UUID referenceId) {
		settle(referenceId, BalanceTransactionType.CONSUME);
	}

	@Override
	public void reverse(UUID referenceId) {
		settle(referenceId, BalanceTransactionType.REVERSAL);
	}

	@Override
	public BalanceSnapshot adjust(UUID employeeId, UUID leaveTypeId, UUID leavePeriodId, BigDecimal amount,
			String reason) {
		if (amount.signum() == 0 || !HalfDaysValidator.isHalfDays(amount)) {
			throw new ApiException(BalanceErrorCode.INVALID_AMOUNT, "Adjust by a non-zero number of whole or half days");
		}
		LeaveTypeInfo type = requireLeaveType(leaveTypeId);
		if (!type.balanceTracked()) {
			throw new ApiException(BalanceErrorCode.LEAVE_TYPE_NOT_TRACKED, type.name() + " has no balance");
		}
		requireEmployee(employeeId);
		LeavePeriodInfo period = policyApi.findPeriod(leavePeriodId)
				.orElseThrow(() -> new ApiException(BalanceErrorCode.LEAVE_PERIOD_NOT_FOUND, "Leave period not found"));
		requireOpen(period);

		LeaveBalance balance = ledger.lock(employeeId, leaveTypeId, leavePeriodId);
		if (amount.signum() < 0 && balance.getAvailable().add(amount).signum() < 0) {
			throw new ApiException(BalanceErrorCode.INSUFFICIENT_BALANCE,
					"Only " + balance.getAvailable() + " day(s) available to remove",
					Map.of("available", balance.getAvailable(), "requested", amount.negate()));
		}
		ledger.post(balance, BalanceTransactionType.ADJUSTMENT, amount, BalanceReferenceType.MANUAL_ADJUSTMENT, null,
				reason.trim());
		events.publishEvent(new BalanceAdjusted(TenantContext.require().id(), employeeId, leaveTypeId, leavePeriodId,
				amount, reason.trim()));
		return mapper.toSnapshot(balance);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<BalanceSnapshot> find(UUID employeeId, UUID leaveTypeId, UUID leavePeriodId) {
		return balanceRepository.findByEmployeeIdAndLeaveTypeIdAndLeavePeriodId(employeeId, leaveTypeId, leavePeriodId)
				.map(mapper::toSnapshot);
	}

	@Override
	@Transactional(readOnly = true)
	public List<BalanceSnapshot> findForEmployee(UUID employeeId, UUID leavePeriodId) {
		return balanceRepository.findAllByEmployeeIdAndLeavePeriodId(employeeId, leavePeriodId).stream()
				.map(mapper::toSnapshot)
				.toList();
	}

	/** Moves whatever the request still holds (release/consume) or has used (reverse). Idempotent. */
	private void settle(UUID referenceId, BalanceTransactionType type) {
		List<LeaveBalanceTransaction> history = transactionRepository.findAllByReferenceId(referenceId);
		if (history.isEmpty()) {
			return;
		}
		LeaveBalance balance = ledger.lock(history.getFirst().getLeaveBalanceId());
		// Re-read under the lock: a concurrent settle of the same request may have committed meanwhile.
		Outstanding outstanding = outstanding(transactionRepository.findAllByReferenceId(referenceId));
		BigDecimal amount = type == BalanceTransactionType.REVERSAL ? outstanding.used() : outstanding.held();
		if (amount.signum() > 0) {
			ledger.post(balance, type, amount, BalanceReferenceType.LEAVE_REQUEST, referenceId, null);
		}
	}

	private static Outstanding outstanding(List<LeaveBalanceTransaction> history) {
		BigDecimal held = BigDecimal.ZERO;
		BigDecimal used = BigDecimal.ZERO;
		for (LeaveBalanceTransaction entry : history) {
			switch (entry.getType()) {
				case HOLD -> held = held.add(entry.getAmount());
				case RELEASE -> held = held.subtract(entry.getAmount());
				case CONSUME -> {
					held = held.subtract(entry.getAmount());
					used = used.add(entry.getAmount());
				}
				case REVERSAL -> used = used.subtract(entry.getAmount());
				default -> {
				}
			}
		}
		return new Outstanding(held, used);
	}

	private LeaveTypeInfo requireLeaveType(UUID leaveTypeId) {
		return policyApi.findLeaveType(leaveTypeId)
				.orElseThrow(() -> new ApiException(BalanceErrorCode.LEAVE_TYPE_NOT_FOUND, "Leave type not found"));
	}

	private EmployeeSummary requireEmployee(UUID employeeId) {
		return organizationApi.findEmployee(employeeId)
				.orElseThrow(() -> new ApiException(BalanceErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found"));
	}

	private static void requireOpen(LeavePeriodInfo period) {
		if (period.status() == LeavePeriodStatus.CLOSED) {
			throw new ApiException(BalanceErrorCode.LEAVE_PERIOD_CLOSED, "Leave period " + period.name() + " is closed");
		}
	}

	private static void requirePositiveHalfDays(BigDecimal days) {
		if (days == null || days.signum() <= 0 || !HalfDaysValidator.isHalfDays(days)) {
			throw new ApiException(BalanceErrorCode.INVALID_AMOUNT, "Days must be a positive number of whole or half days");
		}
	}

	private record Outstanding(BigDecimal held, BigDecimal used) {
	}

}
