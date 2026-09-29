package com.bsolz.lms.leave.domain;

import com.bsolz.lms.leave.model.enums.LeaveAction;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import java.util.Map;
import java.util.Optional;

/**
 * Allowed leave status transitions. Submission creates a request in PENDING.
 * <pre>
 * PENDING              --APPROVE-->              APPROVED
 * PENDING              --REJECT-->               REJECTED
 * PENDING              --WITHDRAW-->             WITHDRAWN
 * APPROVED             --CANCEL-->               CANCELLED              (before it starts, or by HR)
 * APPROVED             --REQUEST_CANCELLATION--> CANCELLATION_PENDING   (once it has started)
 * CANCELLATION_PENDING --APPROVE_CANCELLATION--> CANCELLED
 * CANCELLATION_PENDING --REJECT_CANCELLATION-->  APPROVED
 * </pre>
 */
public final class LeaveStateMachine {

	private static final Map<LeaveStatus, Map<LeaveAction, LeaveStatus>> TRANSITIONS = Map.of(
			LeaveStatus.PENDING, Map.of(
					LeaveAction.APPROVE, LeaveStatus.APPROVED,
					LeaveAction.REJECT, LeaveStatus.REJECTED,
					LeaveAction.WITHDRAW, LeaveStatus.WITHDRAWN),
			LeaveStatus.APPROVED, Map.of(
					LeaveAction.CANCEL, LeaveStatus.CANCELLED,
					LeaveAction.REQUEST_CANCELLATION, LeaveStatus.CANCELLATION_PENDING),
			LeaveStatus.CANCELLATION_PENDING, Map.of(
					LeaveAction.APPROVE_CANCELLATION, LeaveStatus.CANCELLED,
					LeaveAction.REJECT_CANCELLATION, LeaveStatus.APPROVED));

	private LeaveStateMachine() {
	}

	/** The status {@code action} leads to from {@code current}; empty if not allowed. */
	public static Optional<LeaveStatus> next(LeaveStatus current, LeaveAction action) {
		return Optional.ofNullable(TRANSITIONS.getOrDefault(current, Map.of()).get(action));
	}

}
