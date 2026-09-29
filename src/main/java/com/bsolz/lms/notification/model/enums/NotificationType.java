package com.bsolz.lms.notification.model.enums;

/** What a notification is about; the frontend may pick an icon or a link target from it. */
public enum NotificationType {

	/** To the approvers of a step that just became current. */
	APPROVAL_REQUESTED,

	/** To the approvers of a step waiting longer than its workflow's reminder interval. */
	APPROVAL_REMINDER,

	/** To approvers added to an overdue step. */
	APPROVAL_ESCALATED,

	/** To the requester. */
	LEAVE_APPROVED,

	LEAVE_REJECTED,

	/** To the requester when someone else cancelled their leave, else to their manager. */
	LEAVE_CANCELLED,

	CANCELLATION_APPROVED,

	CANCELLATION_REJECTED,

	/** To the employee and their manager, the day before approved leave starts. */
	LEAVE_STARTING,

	/** To the employee whose balance HR adjusted. */
	BALANCE_ADJUSTED

}
