package com.bsolz.lms.audit.model.enums;

/** What an activity log entry records. */
public enum AuditAction {

	EMPLOYEE_CREATED,
	EMPLOYEE_EXITED,

	LEAVE_SUBMITTED,
	LEAVE_APPROVED,
	LEAVE_REJECTED,
	LEAVE_WITHDRAWN,
	LEAVE_CANCELLED,
	LEAVE_CANCELLATION_REQUESTED,
	LEAVE_CANCELLATION_APPROVED,
	LEAVE_CANCELLATION_REJECTED,

	/** A step approved while later steps remain; the final decision is recorded on the leave request. */
	APPROVAL_STEP_APPROVED,

	/** An overdue approval step given additional approvers. */
	APPROVAL_ESCALATED,

	BALANCE_ADJUSTED,

	/** Year-end processing of a leave period: carry forward, lapse, close. */
	LEAVE_PERIOD_CLOSED

}
