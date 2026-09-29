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
	LEAVE_REQUEST

}
