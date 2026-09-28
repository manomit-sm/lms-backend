package com.bsolz.lms.balance.service;

import com.bsolz.lms.balance.domain.Proration;
import com.bsolz.lms.balance.entity.LeaveBalance;
import com.bsolz.lms.balance.exception.BalanceErrorCode;
import com.bsolz.lms.balance.model.enums.BalanceReferenceType;
import com.bsolz.lms.balance.model.enums.BalanceTransactionType;
import com.bsolz.lms.balance.repository.LeaveBalanceTransactionRepository;
import com.bsolz.lms.balance.web.dto.AllocationResult;
import com.bsolz.lms.leavepolicy.api.LeavePeriodInfo;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.leavepolicy.api.LeaveTypeInfo;
import com.bsolz.lms.leavepolicy.api.ResolvedPolicy;
import com.bsolz.lms.leavepolicy.model.enums.AccrualMethod;
import com.bsolz.lms.leavepolicy.model.enums.LeavePeriodStatus;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.settings.api.SettingsApi;
import com.bsolz.lms.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Allocates each leave period's entitlement: for a new employee when they are created, and in bulk for
 * a period (at its start, or to catch up). A balance receives its allocation at most once, so both are
 * safe to repeat. Upfront policies allocate the entitlement (prorated for joiners when the policy says
 * so); accruing policies only get their balance created here, the accrual job credits them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationService {

	private final BalanceLedger ledger;

	private final LeaveBalanceTransactionRepository transactionRepository;

	private final OrganizationApi organizationApi;

	private final LeavePolicyApi policyApi;

	private final SettingsApi settings;

	private final TransactionTemplate transactionTemplate;

	/** Allocates the period that covers the later of the employee's joining date and today. */
	@Transactional
	public void allocateForNewEmployee(UUID employeeId) {
		Optional<EmployeeSummary> found = organizationApi.findEmployee(employeeId);
		if (found.isEmpty() || found.get().isExited()) {
			return;
		}
		EmployeeSummary employee = found.get();
		LocalDate today = settings.today();
		LocalDate from = employee.joiningDate().isAfter(today) ? employee.joiningDate() : today;
		Optional<LeavePeriodInfo> period = policyApi.findPeriodContaining(from);
		if (period.isEmpty()) {
			log.warn("No leave period covers {}; employee {} gets their balances at the next allocation", from,
					employeeId);
			return;
		}
		allocate(employee, period.get(), trackedTypes(null), BalanceReferenceType.EMPLOYEE_JOINING, employeeId);
	}

	/** Allocates the period to every current employee, one transaction per employee. */
	public AllocationResult allocatePeriod(UUID leavePeriodId, Set<UUID> leaveTypeIds) {
		LeavePeriodInfo period = leavePeriodId != null
				? policyApi.findPeriod(leavePeriodId).orElseThrow(
						() -> new ApiException(BalanceErrorCode.LEAVE_PERIOD_NOT_FOUND, "Leave period not found"))
				: policyApi.findPeriodContaining(settings.today()).orElseThrow(
						() -> new ApiException(BalanceErrorCode.NO_LEAVE_PERIOD, "No leave period covers today"));
		if (period.status() == LeavePeriodStatus.CLOSED) {
			throw new ApiException(BalanceErrorCode.LEAVE_PERIOD_CLOSED, "Leave period " + period.name() + " is closed");
		}
		List<LeaveTypeInfo> types = trackedTypes(leaveTypeIds);
		List<EmployeeSummary> employees = organizationApi.findCurrentEmployees();
		int allocated = 0;
		int alreadyAllocated = 0;
		for (EmployeeSummary employee : employees) {
			Outcome outcome = transactionTemplate.execute(status -> allocate(employee, period, types,
					BalanceReferenceType.BULK_ALLOCATION, period.id()));
			allocated += outcome.allocated();
			alreadyAllocated += outcome.alreadyAllocated();
		}
		return new AllocationResult(period.id(), employees.size(), allocated, alreadyAllocated);
	}

	private Outcome allocate(EmployeeSummary employee, LeavePeriodInfo period, List<LeaveTypeInfo> types,
			BalanceReferenceType referenceType, UUID referenceId) {
		if (employee.isExited() || employee.joiningDate().isAfter(period.endDate())) {
			return Outcome.NONE;
		}
		LocalDate asOf = employee.joiningDate().isAfter(period.startDate()) ? employee.joiningDate()
				: period.startDate();
		int allocated = 0;
		int alreadyAllocated = 0;
		for (LeaveTypeInfo type : types) {
			Optional<ResolvedPolicy> policy = policyApi.resolve(employee, type.id(), asOf);
			if (policy.isEmpty()) {
				continue;
			}
			LeaveBalance balance = ledger.lock(employee.id(), type.id(), period.id());
			if (transactionRepository.existsByLeaveBalanceIdAndType(balance.getId(), BalanceTransactionType.ALLOCATION)) {
				alreadyAllocated++;
				continue;
			}
			BigDecimal amount = entitlement(policy.get(), period, employee.joiningDate());
			if (amount.signum() > 0) {
				String note = amount.compareTo(policy.get().entitlementDays()) == 0
						? policy.get().policyName()
						: policy.get().policyName() + ": prorated from " + policy.get().entitlementDays()
								+ " (joined " + employee.joiningDate() + ")";
				ledger.post(balance, BalanceTransactionType.ALLOCATION, amount, referenceType, referenceId, note);
				allocated++;
			}
		}
		return new Outcome(allocated, alreadyAllocated);
	}

	private static BigDecimal entitlement(ResolvedPolicy policy, LeavePeriodInfo period, LocalDate joiningDate) {
		if (policy.accrualMethod() != AccrualMethod.UPFRONT) {
			return BigDecimal.ZERO;
		}
		return policy.prorateOnJoining()
				? Proration.prorate(policy.entitlementDays(), period.startDate(), period.endDate(), joiningDate)
				: policy.entitlementDays();
	}

	private List<LeaveTypeInfo> trackedTypes(Set<UUID> only) {
		List<LeaveTypeInfo> types = policyApi.findActiveLeaveTypes().stream()
				.filter(LeaveTypeInfo::balanceTracked)
				.filter(type -> only == null || only.isEmpty() || only.contains(type.id()))
				.toList();
		if (only != null && types.size() < only.size()) {
			throw new ApiException(BalanceErrorCode.LEAVE_TYPE_NOT_TRACKED,
					"Every leave type must exist, be active and be balance-tracked");
		}
		return types;
	}

	private record Outcome(int allocated, int alreadyAllocated) {

		static final Outcome NONE = new Outcome(0, 0);

	}

}
