package com.bsolz.lms.balance.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Leave balances for other modules. Every change locks the balance row ({@code SELECT ... FOR UPDATE})
 * and writes a ledger row in the caller's transaction, so concurrent requests can never spend the same
 * days twice. All calls need a tenant bound.
 * <p>
 * A request's life: {@link #hold} on submission, then {@link #consume} on approval or {@link #release}
 * on rejection/withdrawal; {@link #reverse} gives consumed days back when approved leave is cancelled.
 * The last three are keyed by the request id, idempotent, and do nothing for a request that holds
 * nothing (e.g. one for a leave type that isn't balance-tracked).
 */
public interface BalanceApi {

	/**
	 * Holds days as pending against the balance of the leave period containing {@code leaveDate}.
	 * Empty (and nothing recorded) when the leave type isn't balance-tracked. Fails with
	 * {@code INSUFFICIENT_BALANCE} if it would take the balance below what the policy allows.
	 */
	Optional<BalanceSnapshot> hold(HoldRequest request);

	/** Returns the request's pending days to the balance. */
	void release(UUID referenceId);

	/** Turns the request's pending days into used days. */
	void consume(UUID referenceId);

	/** Returns the request's used days to the balance. */
	void reverse(UUID referenceId);

	/** A manual correction (positive or negative); {@code reason} is required. */
	BalanceSnapshot adjust(UUID employeeId, UUID leaveTypeId, UUID leavePeriodId, BigDecimal amount, String reason);

	Optional<BalanceSnapshot> find(UUID employeeId, UUID leaveTypeId, UUID leavePeriodId);

	List<BalanceSnapshot> findForEmployee(UUID employeeId, UUID leavePeriodId);

}
