package com.bsolz.lms.leave.service;

import com.bsolz.lms.approval.api.ApprovalCompleted;
import com.bsolz.lms.approval.api.ApprovalRejected;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Applies approval outcomes to leave requests. Deliberately a synchronous {@code @EventListener}, not an
 * after-commit one: the decision, the leave status and the balance change commit together or not at
 * all, and the approver's response already shows the result.
 */
@Component
@RequiredArgsConstructor
class ApprovalOutcomeListener {

	private final LeaveRequestService service;

	@EventListener
	void on(ApprovalCompleted event) {
		switch (event.subjectType()) {
			case LEAVE_REQUEST -> service.approved(event.subjectId(), event.approvedByUserId());
			case LEAVE_CANCELLATION -> service.cancellationApproved(event.subjectId(), event.approvedByUserId());
		}
	}

	@EventListener
	void on(ApprovalRejected event) {
		switch (event.subjectType()) {
			case LEAVE_REQUEST -> service.rejected(event.subjectId(), event.rejectedByUserId(), event.comment());
			case LEAVE_CANCELLATION -> service.cancellationRejected(event.subjectId(), event.rejectedByUserId(),
					event.comment());
		}
	}

}
