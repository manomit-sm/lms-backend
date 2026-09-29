package com.bsolz.lms.approval.api;

import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import java.util.List;
import java.util.UUID;

/**
 * Approvals for other modules. All calls need a tenant bound and join the caller's transaction.
 * <p>
 * The outcome is announced by {@link ApprovalCompleted} / {@link ApprovalRejected}, published in the
 * deciding transaction. The subject's owner listens synchronously ({@code @EventListener}) so that its
 * state changes commit or roll back together with the decision.
 */
public interface ApprovalApi {

	/**
	 * Picks the workflow, resolves each step's approvers (never the requester) and snapshots them. If
	 * no step has an approver the approval completes at once and {@link ApprovalCompleted} is published
	 * before this returns.
	 */
	ApprovalStarted start(StartApproval request);

	/**
	 * Cancels the subject's pending approval, if any. No event is published. Locks the approval, so call
	 * it before locking the subject: deciding locks the approval first too.
	 */
	void cancel(ApprovalSubjectType subjectType, UUID subjectId);

	/** Every approval of the subject, oldest first. */
	List<ApprovalView> findApprovals(UUID subjectId);

	/** Tasks currently waiting for the user, oldest first. */
	List<PendingTask> findPendingTasks(UUID userId);

	/** Whether the user is or was an assignee on any of the subject's approvals. */
	boolean isApprover(UUID userId, UUID subjectId);

}
