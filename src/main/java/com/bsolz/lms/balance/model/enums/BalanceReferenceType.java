package com.bsolz.lms.balance.model.enums;

/** What caused a ledger entry; {@code referenceId} points at it where there is one. */
public enum BalanceReferenceType {

	/** referenceId: the employee */
	EMPLOYEE_JOINING,

	/** referenceId: the leave period */
	BULK_ALLOCATION,

	/** no referenceId; the reason is in the note */
	MANUAL_ADJUSTMENT,

	/** referenceId: the leave request */
	LEAVE_REQUEST,

	/** Monthly or quarterly accrual; referenceId: the leave period, the installment is in the note */
	ACCRUAL,

	/**
	 * Year-end processing of an ended period. referenceId: the other period - the next one on the ended
	 * period's CARRY_OUT and EXPIRY, the ended one on the next period's CARRY_FORWARD.
	 */
	PERIOD_ROLLOVER,

	/** Carried-forward days expiring part-way through a period; referenceId: that period */
	CARRY_FORWARD_EXPIRY

}
