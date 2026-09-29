package com.bsolz.lms.balance.service;

import com.bsolz.lms.balance.api.LeavePeriodRolledOver;
import com.bsolz.lms.balance.domain.AccrualSchedule;
import com.bsolz.lms.balance.entity.LeaveBalance;
import com.bsolz.lms.balance.model.enums.BalanceReferenceType;
import com.bsolz.lms.balance.model.enums.BalanceTransactionType;
import com.bsolz.lms.balance.repository.LeaveBalanceRepository;
import com.bsolz.lms.balance.repository.LeaveBalanceTransactionRepository;
import com.bsolz.lms.leavepolicy.api.LeavePeriodInfo;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.leavepolicy.api.LeaveTypeInfo;
import com.bsolz.lms.leavepolicy.api.ResolvedPolicy;
import com.bsolz.lms.leavepolicy.model.enums.AccrualMethod;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.shared.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The daily balance job for one tenant, in this order (each step is idempotent, so a missed or repeated
 * run catches up without doing anything twice):
 * <ol>
 * <li><b>Open</b> the period containing today and the one {@value #OPEN_AHEAD_DAYS} days ahead if they
 * don't exist, and allocate them (upfront entitlements; see {@link AllocationService}), so the next
 * leave year exists - with its balances - before it starts. Employees joining later get their
 * allocation when they are created.</li>
 * <li><b>Accrue</b> monthly/quarterly installments that are due, in every open period.</li>
 * <li><b>Year end</b> for open periods that have ended: carry unused days forward up to the policy's
 * limit, let the rest lapse, then close the period.</li>
 * <li><b>Expire</b> carried-forward days the policy only keeps for a number of months.</li>
 * </ol>
 * Every balance is changed in its own transaction, under its row lock like any other balance change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceMaintenanceService {

	static final int OPEN_AHEAD_DAYS = 30;

	private final BalanceLedger ledger;

	private final LeaveBalanceRepository balanceRepository;

	private final LeaveBalanceTransactionRepository transactionRepository;

	private final AllocationService allocationService;

	private final LeavePolicyApi policyApi;

	private final OrganizationApi organizationApi;

	private final ApplicationEventPublisher events;

	private final TransactionTemplate transactionTemplate;

	public Result run(LocalDate today) {
		int allocated = 0;
		for (LocalDate date : List.of(today, today.plusDays(OPEN_AHEAD_DAYS))) {
			if (policyApi.findPeriodContaining(date).isEmpty()) {
				LeavePeriodInfo opened = policyApi.openPeriodContaining(date);
				log.info("Opened leave period {} ({} to {})", opened.name(), opened.startDate(), opened.endDate());
				allocated += allocationService.allocatePeriod(opened.id(), null).balancesAllocated();
			}
		}
		int accrued = 0;
		for (LeavePeriodInfo period : policyApi.findOpenPeriods()) {
			if (!period.startDate().isAfter(today)) {
				accrued += accrue(period, today);
			}
		}
		int closed = 0;
		for (LeavePeriodInfo period : policyApi.findOpenPeriods()) {
			if (period.endDate().isBefore(today)) {
				closeYear(period);
				closed++;
			}
		}
		int expired = 0;
		for (LeavePeriodInfo period : policyApi.findOpenPeriods()) {
			if (!period.startDate().isAfter(today)) {
				expired += expireCarriedForward(period, today);
			}
		}
		return new Result(allocated, accrued, closed, expired);
	}

	/** Credits every installment due by today that a balance hasn't had yet. */
	private int accrue(LeavePeriodInfo period, LocalDate today) {
		List<LeaveTypeInfo> types = policyApi.findActiveLeaveTypes().stream().filter(LeaveTypeInfo::balanceTracked)
				.toList();
		int credited = 0;
		for (EmployeeSummary employee : organizationApi.findCurrentEmployees()) {
			if (employee.joiningDate().isAfter(period.endDate()) || employee.joiningDate().isAfter(today)) {
				continue;
			}
			LocalDate asOf = employee.joiningDate().isAfter(period.startDate()) ? employee.joiningDate()
					: period.startDate();
			for (LeaveTypeInfo type : types) {
				Optional<ResolvedPolicy> policy = policyApi.resolve(employee, type.id(), asOf);
				if (policy.isEmpty() || policy.get().accrualMethod() == AccrualMethod.UPFRONT) {
					continue;
				}
				List<AccrualSchedule.Installment> due = AccrualSchedule.installments(policy.get().accrualMethod(),
						policy.get().entitlementDays(), period.startDate(), period.endDate(), employee.joiningDate())
						.stream().filter(installment -> !installment.date().isAfter(today)).toList();
				if (due.isEmpty() || due.size() <= creditedSoFar(employee.id(), type.id(), period.id())) {
					continue;
				}
				credited += transactionTemplate.execute(status -> {
					LeaveBalance balance = ledger.lock(employee.id(), type.id(), period.id());
					int done = (int) transactionRepository.countByLeaveBalanceIdAndType(balance.getId(),
							BalanceTransactionType.ACCRUAL);
					for (AccrualSchedule.Installment installment : due.subList(Math.min(done, due.size()), due.size())) {
						ledger.post(balance, BalanceTransactionType.ACCRUAL, installment.amount(),
								BalanceReferenceType.ACCRUAL, period.id(),
								policy.get().policyName() + ": " + installment.label());
					}
					return Math.max(0, due.size() - done);
				});
			}
		}
		return credited;
	}

	private long creditedSoFar(UUID employeeId, UUID leaveTypeId, UUID leavePeriodId) {
		return balanceRepository.findByEmployeeIdAndLeaveTypeIdAndLeavePeriodId(employeeId, leaveTypeId, leavePeriodId)
				.map(balance -> transactionRepository.countByLeaveBalanceIdAndType(balance.getId(),
						BalanceTransactionType.ACCRUAL))
				.orElse(0L);
	}

	/**
	 * Year end of an ended period: each balance's available days move to the next period up to the
	 * policy's carry-forward limit (as of the period's last day) and the rest lapse. Exited employees
	 * carry nothing. A negative balance is left as it is.
	 */
	private void closeYear(LeavePeriodInfo ended) {
		LeavePeriodInfo next = policyApi.openPeriodContaining(ended.endDate().plusDays(1));
		BigDecimal[] totals = { BigDecimal.ZERO, BigDecimal.ZERO };
		List<UUID> balanceIds = balanceRepository.findIdsByLeavePeriodId(ended.id());
		for (UUID balanceId : balanceIds) {
			transactionTemplate.executeWithoutResult(status -> {
				BigDecimal[] moved = rollBalance(balanceId, ended, next);
				totals[0] = totals[0].add(moved[0]);
				totals[1] = totals[1].add(moved[1]);
			});
		}
		transactionTemplate.executeWithoutResult(status -> {
			policyApi.closePeriod(ended.id());
			events.publishEvent(new LeavePeriodRolledOver(TenantContext.require().id(), ended.id(), ended.name(),
					next.id(), balanceIds.size(), totals[0], totals[1]));
		});
		log.info("Closed leave period {}: {} day(s) carried forward to {}, {} lapsed", ended.name(), totals[0],
				next.name(), totals[1]);
	}

	/** @return the days carried forward and the days lapsed */
	private BigDecimal[] rollBalance(UUID balanceId, LeavePeriodInfo ended, LeavePeriodInfo next) {
		LeaveBalance balance = ledger.lock(balanceId);
		BigDecimal available = balance.getAvailable();
		if (available.signum() <= 0 || transactionRepository.existsByLeaveBalanceIdAndReferenceType(balanceId,
				BalanceReferenceType.PERIOD_ROLLOVER)) {
			return new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO };
		}
		BigDecimal carry = organizationApi.findEmployee(balance.getEmployeeId())
				.filter(employee -> !employee.isExited())
				.flatMap(employee -> policyApi.resolve(employee, balance.getLeaveTypeId(), ended.endDate()))
				.map(policy -> policy.carryForwardMaxDays().min(available))
				.orElse(BigDecimal.ZERO);
		BigDecimal lapse = available.subtract(carry);
		if (carry.signum() > 0) {
			ledger.post(balance, BalanceTransactionType.CARRY_OUT, carry, BalanceReferenceType.PERIOD_ROLLOVER,
					next.id(), "Carried forward to " + next.name());
			LeaveBalance target = ledger.lock(balance.getEmployeeId(), balance.getLeaveTypeId(), next.id());
			ledger.post(target, BalanceTransactionType.CARRY_FORWARD, carry, BalanceReferenceType.PERIOD_ROLLOVER,
					ended.id(), "Carried forward from " + ended.name());
		}
		if (lapse.signum() > 0) {
			ledger.post(balance, BalanceTransactionType.EXPIRY, lapse, BalanceReferenceType.PERIOD_ROLLOVER, next.id(),
					"Unused at the end of " + ended.name());
		}
		return new BigDecimal[] { carry, lapse };
	}

	/**
	 * Expires carried-forward days once the policy's expiry (months after the period starts) has passed.
	 * Leave taken in the period is counted against the carried-forward days first, so only what is left of
	 * them expires.
	 *
	 * @return the number of balances with days expired
	 */
	private int expireCarriedForward(LeavePeriodInfo period, LocalDate today) {
		int expired = 0;
		for (UUID balanceId : balanceRepository.findIdsWithCarriedForward(period.id())) {
			Boolean done = transactionTemplate.execute(status -> {
				if (transactionRepository.existsByLeaveBalanceIdAndReferenceType(balanceId,
						BalanceReferenceType.CARRY_FORWARD_EXPIRY)) {
					return false;
				}
				LeaveBalance balance = balanceRepository.findById(balanceId).orElseThrow();
				Optional<Integer> months = organizationApi.findEmployee(balance.getEmployeeId())
						.flatMap(employee -> policyApi.resolve(employee, balance.getLeaveTypeId(), period.startDate()))
						.map(ResolvedPolicy::carryForwardExpiryMonths);
				if (months.isEmpty() || today.isBefore(period.startDate().plusMonths(months.get()))) {
					return false;
				}
				LeaveBalance locked = ledger.lock(balanceId);
				BigDecimal unusedCarried = locked.getCarriedForward().subtract(locked.getUsed())
						.subtract(locked.getPending()).max(BigDecimal.ZERO).min(locked.getAvailable());
				if (unusedCarried.signum() <= 0) {
					return false;
				}
				ledger.post(locked, BalanceTransactionType.EXPIRY, unusedCarried,
						BalanceReferenceType.CARRY_FORWARD_EXPIRY, period.id(),
						"Carried-forward days expired on " + period.startDate().plusMonths(months.get()));
				return true;
			});
			if (Boolean.TRUE.equals(done)) {
				expired++;
			}
		}
		return expired;
	}

	/**
	 * @param allocated balances given their upfront allocation in newly opened periods
	 * @param accrued accrual installments credited
	 * @param closedPeriods periods whose year end was processed
	 * @param expiredBalances balances whose carried-forward days expired
	 */
	public record Result(int allocated, int accrued, int closedPeriods, int expiredBalances) {
	}

}
