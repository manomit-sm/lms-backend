package com.bsolz.lms.approval.entity;

import com.bsolz.lms.approval.model.enums.ApprovalTaskStatus;
import com.bsolz.lms.approval.model.enums.ApproverType;
import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One step of an approval, with the approvers resolved when the approval started. */
@Getter
@Entity
@Table(name = "approval_task")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalTask extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "approval_request_id")
	private ApprovalRequest approvalRequest;

	private int stepOrder;

	@Enumerated(EnumType.STRING)
	private ApproverType approverType;

	private String approverDescription;

	@Enumerated(EnumType.STRING)
	private ApprovalTaskStatus status;

	private UUID actedByUserId;

	private Instant actedAt;

	private String comment;

	/** When the step became current; its deadlines count from here. */
	private Instant activatedAt;

	private Instant lastRemindedAt;

	private Instant escalatedAt;

	@ElementCollection
	@CollectionTable(name = "approval_task_assignee", joinColumns = @JoinColumn(name = "approval_task_id"))
	@Column(name = "user_id")
	private Set<UUID> assigneeUserIds = new HashSet<>();

	ApprovalTask(ApprovalRequest approvalRequest, int stepOrder, ApproverType approverType, String description,
			Set<UUID> assignees, String skipReason) {
		this.approvalRequest = approvalRequest;
		this.stepOrder = stepOrder;
		this.approverType = approverType;
		this.approverDescription = description;
		this.assigneeUserIds.addAll(assignees);
		this.status = assignees.isEmpty() ? ApprovalTaskStatus.SKIPPED : ApprovalTaskStatus.WAITING;
		this.comment = assignees.isEmpty() ? skipReason : null;
	}

	public boolean isAssignee(UUID userId) {
		return assigneeUserIds.contains(userId);
	}

	void activate(Instant at) {
		status = ApprovalTaskStatus.PENDING;
		activatedAt = at;
	}

	public void reminded(Instant at) {
		lastRemindedAt = at;
	}

	/** Adds approvers once the step is overdue; recorded even when there was nobody to add. */
	public void escalate(Set<UUID> additionalAssignees, Instant at) {
		assigneeUserIds.addAll(additionalAssignees);
		escalatedAt = at;
	}

	void decide(ApprovalTaskStatus decision, UUID userId, String decisionComment, Instant at) {
		status = decision;
		actedByUserId = userId;
		comment = decisionComment;
		actedAt = at;
	}

	void cancel() {
		status = ApprovalTaskStatus.CANCELLED;
	}

}
