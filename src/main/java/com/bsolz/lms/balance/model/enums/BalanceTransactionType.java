package com.bsolz.lms.balance.model.enums;

/** A ledger entry's effect on the balance buckets. Amounts are positive except for ADJUSTMENT. */
public enum BalanceTransactionType {

	/** allocated += amount (entitlement for the period) */
	ALLOCATION,

	/** allocated += amount (periodic accrual) */
	ACCRUAL,

	/** carriedForward += amount */
	CARRY_FORWARD,

	/** adjusted += amount (signed) */
	ADJUSTMENT,

	/** expired += amount */
	EXPIRY,

	/** pending += amount */
	HOLD,

	/** pending -= amount */
	RELEASE,

	/** pending -= amount, used += amount */
	CONSUME,

	/** used -= amount */
	REVERSAL

}
