package com.bsolz.lms.approval.entity;

import com.bsolz.lms.approval.model.enums.ApprovalStatus;
import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import com.bsolz.lms.approval.model.enums.ApprovalTaskStatus;
import com.bsolz.lms.approval.model.enums.ApproverType;
import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One run of a workflow for a subject. Steps are decided one at a time, in order; skipped steps
 * are passed over. Status changes only through the methods below.
 */
@Getter
@Entity
@Table(name = "approval_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalRequest extends BaseEntity {

	@Enumerated(EnumType.STRING)
	private ApprovalSubjectType subjectType;

	private UUID subjectId;

	private UUID requesterEmployeeId;

	private UUID requesterUserId;

	@Column(name = "approval_workflow_id")
	private UUID workflowId;

	private String workflowName;

	@Enumerated(EnumType.STRING)
	private ApprovalStatus status;

	private Instant completedAt;

	@OneToMany(mappedBy = "approvalRequest", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("stepOrder")
	private List<ApprovalTask> tasks = new ArrayList<>();

	public ApprovalRequest(ApprovalSubjectType subjectType, UUID subjectId, UUID requesterEmployeeId,
			UUID requesterUserId, ApprovalWorkflow workflow) {
		this.subjectType = subjectType;
		this.subjectId = subjectId;
		this.requesterEmployeeId = requesterEmployeeId;
		this.requesterUserId = requesterUserId;
		this.workflowId = workflow.getId();
		this.workflowName = workflow.getName();
		this.status = ApprovalStatus.PENDING;
	}

	public void addStep(ApproverType approverType, String description, Set<UUID> assignees, String skipReason) {
		tasks.add(new ApprovalTask(this, tasks.size() + 1, approverType, description, assignees, skipReason));
	}

	public Optional<ApprovalTask> currentTask() {
		return tasks.stream().filter(task -> task.getStatus() == ApprovalTaskStatus.PENDING).findFirst();
	}

	/**
	 * Makes the next waiting step current. With none left the approval is approved.
	 *
	 * @return the new current step, or empty if the approval is now approved
	 */
	public Optional<ApprovalTask> advance(Instant at) {
		Optional<ApprovalTask> next = tasks.stream()
				.filter(task -> task.getStatus() == ApprovalTaskStatus.WAITING)
				.findFirst();
		next.ifPresentOrElse(ApprovalTask::activate, () -> finish(ApprovalStatus.APPROVED, at));
		return next;
	}

	/** Approves the current step, then advances. */
	public Optional<ApprovalTask> approve(ApprovalTask task, UUID userId, String comment, Instant at) {
		task.decide(ApprovalTaskStatus.APPROVED, userId, comment, at);
		return advance(at);
	}

	public void reject(ApprovalTask task, UUID userId, String comment, Instant at) {
		task.decide(ApprovalTaskStatus.REJECTED, userId, comment, at);
		finish(ApprovalStatus.REJECTED, at);
	}

	public void cancel(Instant at) {
		finish(ApprovalStatus.CANCELLED, at);
	}

	private void finish(ApprovalStatus outcome, Instant at) {
		status = outcome;
		completedAt = at;
		tasks.stream()
				.filter(task -> task.getStatus() == ApprovalTaskStatus.WAITING
						|| task.getStatus() == ApprovalTaskStatus.PENDING)
				.forEach(ApprovalTask::cancel);
	}

}
