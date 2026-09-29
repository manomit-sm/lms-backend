package com.bsolz.lms.approval.model.enums;

public enum ApprovalTaskStatus {

	/** A later step, waiting for the earlier ones. */
	WAITING,

	/** The current step: its assignees can approve or reject. */
	PENDING,

	APPROVED,
	REJECTED,

	/** Nobody could approve the step (e.g. no reporting manager, or only the requester). */
	SKIPPED,

	/** The approval ended (rejected or withdrawn) before this step was reached. */
	CANCELLED

}
