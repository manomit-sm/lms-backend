package com.bsolz.lms.approval.model.enums;

/** What an approval decides. The subject id is always the leave request's id. */
public enum ApprovalSubjectType {

	LEAVE_REQUEST,

	/** Cancelling leave that has already started. */
	LEAVE_CANCELLATION

}
