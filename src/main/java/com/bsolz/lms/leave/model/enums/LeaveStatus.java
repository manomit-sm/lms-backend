package com.bsolz.lms.leave.model.enums;

import java.util.Set;

/** See {@code LeaveStateMachine} for the allowed transitions. */
public enum LeaveStatus {

	/** Submitted; its days are held against the balance while approval runs. */
	PENDING,

	APPROVED,
	REJECTED,

	/** Withdrawn by the employee before a decision. */
	WITHDRAWN,

	/** Approved leave that has started; its cancellation awaits approval. */
	CANCELLATION_PENDING,

	CANCELLED;

	/** Statuses that occupy their dates: a new request may not overlap them. */
	public static final Set<LeaveStatus> LIVE = Set.of(PENDING, APPROVED, CANCELLATION_PENDING);

}
