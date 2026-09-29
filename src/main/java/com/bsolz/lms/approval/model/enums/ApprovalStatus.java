package com.bsolz.lms.approval.model.enums;

public enum ApprovalStatus {

	PENDING,
	APPROVED,
	REJECTED,

	/** Withdrawn by the requester before a decision. */
	CANCELLED

}
