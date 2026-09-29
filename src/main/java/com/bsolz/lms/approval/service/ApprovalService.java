package com.bsolz.lms.approval.service;

import com.bsolz.lms.approval.api.ApprovalApi;
import com.bsolz.lms.approval.api.ApprovalCompleted;
import com.bsolz.lms.approval.api.ApprovalRejected;
import com.bsolz.lms.approval.api.ApprovalStarted;
import com.bsolz.lms.approval.api.ApprovalTaskAssigned;
import com.bsolz.lms.approval.api.ApprovalView;
import com.bsolz.lms.approval.api.PendingTask;
import com.bsolz.lms.approval.api.StartApproval;
import com.bsolz.lms.approval.domain.WorkflowSelector;
import com.bsolz.lms.approval.entity.ApprovalRequest;
import com.bsolz.lms.approval.entity.ApprovalTask;
import com.bsolz.lms.approval.entity.ApprovalWorkflow;
import com.bsolz.lms.approval.entity.WorkflowStep;
import com.bsolz.lms.approval.exception.ApprovalErrorCode;
import com.bsolz.lms.approval.model.enums.ApprovalStatus;
import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import com.bsolz.lms.approval.model.enums.ApprovalTaskStatus;
import com.bsolz.lms.approval.repository.ApprovalRequestRepository;
import com.bsolz.lms.approval.repository.ApprovalTaskRepository;
import com.bsolz.lms.approval.repository.ApprovalWorkflowRepository;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.security.CurrentUser;
import com.bsolz.lms.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs approvals. Decisions on one approval are serialized by locking its row; the outcome events are
 * published inside the deciding transaction (see {@link ApprovalApi}).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ApprovalService implements ApprovalApi {

	private final ApprovalWorkflowRepository workflowRepository;

	private final ApprovalRequestRepository requestRepository;

	private final ApprovalTaskRepository taskRepository;

	private final ApproverResolver approverResolver;

	private final ApprovalViews views;

	private final OrganizationApi organizationApi;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	@Override
	public ApprovalStarted start(StartApproval request) {
		requestRepository.findBySubjectTypeAndSubjectIdAndStatus(request.subjectType(), request.subjectId(),
				ApprovalStatus.PENDING).ifPresent(existing -> {
					throw new ApiException(ApprovalErrorCode.APPROVAL_ALREADY_PENDING,
							"An approval for this request is already pending");
				});
		EmployeeSummary requester = organizationApi.findEmployee(request.requesterEmployeeId())
				.orElseThrow(() -> new IllegalArgumentException("Unknown requester " + request.requesterEmployeeId()));
		ApprovalWorkflow workflow = WorkflowSelector.select(workflowRepository.findAllByOrderByPriorityAsc(),
				request.leaveTypeId(), request.days());

		ApprovalRequest approval = new ApprovalRequest(request.subjectType(), request.subjectId(), requester.id(),
				request.requesterUserId(), workflow);
		for (WorkflowStep step : workflow.getSteps()) {
			ApproverResolver.Resolution resolution = approverResolver.resolve(step, requester, request.requesterUserId());
			approval.addStep(step.approverType(), resolution.description(), resolution.userIds(), resolution.skipReason());
		}
		requestRepository.save(approval);
		publishOutcome(approval, approval.advance(now()), null);
		return new ApprovalStarted(approval.getId(), approval.getStatus());
	}

	/** Approves the current user's task; advances to the next step or completes the approval. */
	public ApprovalView approve(UUID taskId, String comment) {
		UUID userId = CurrentUser.require().userId();
		ApprovalRequest approval = lockApprovalOf(taskId);
		ApprovalTask task = requireDecidable(approval, taskId, userId);
		publishOutcome(approval, approval.approve(task, userId, blankToNull(comment), now()), userId);
		return views.toView(approval);
	}

	/** Rejects the current user's task, which ends the approval. A comment is required. */
	public ApprovalView reject(UUID taskId, String comment) {
		if (comment == null || comment.isBlank()) {
			throw new ApiException(ApprovalErrorCode.COMMENT_REQUIRED, "Say why the request is rejected");
		}
		UUID userId = CurrentUser.require().userId();
		ApprovalRequest approval = lockApprovalOf(taskId);
		ApprovalTask task = requireDecidable(approval, taskId, userId);
		approval.reject(task, userId, comment.trim(), now());
		events.publishEvent(new ApprovalRejected(TenantContext.require().id(), approval.getId(),
				approval.getSubjectType(), approval.getSubjectId(), userId, comment.trim()));
		return views.toView(approval);
	}

	@Override
	public void cancel(ApprovalSubjectType subjectType, UUID subjectId) {
		requestRepository.findBySubjectTypeAndSubjectIdAndStatus(subjectType, subjectId, ApprovalStatus.PENDING)
				.flatMap(pending -> requestRepository.findByIdForUpdate(pending.getId()))
				.filter(approval -> approval.getStatus() == ApprovalStatus.PENDING)
				.ifPresent(approval -> approval.cancel(now()));
	}

	@Override
	@Transactional(readOnly = true)
	public List<ApprovalView> findApprovals(UUID subjectId) {
		return views.toViews(requestRepository.findAllBySubjectIdOrderByCreatedAtAsc(subjectId));
	}

	@Override
	@Transactional(readOnly = true)
	public List<PendingTask> findPendingTasks(UUID userId) {
		return taskRepository.findAssignedWithStatus(userId, ApprovalTaskStatus.PENDING).stream()
				.map(task -> new PendingTask(task.getId(), task.getApprovalRequest().getId(),
						task.getApprovalRequest().getSubjectType(), task.getApprovalRequest().getSubjectId(),
						task.getStepOrder(), task.getApproverDescription(), task.getUpdatedAt()))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isApprover(UUID userId, UUID subjectId) {
		return taskRepository.isAssigneeForSubject(userId, subjectId);
	}

	private ApprovalRequest lockApprovalOf(UUID taskId) {
		UUID approvalId = taskRepository.findApprovalIdByTaskId(taskId)
				.orElseThrow(() -> new ApiException(ApprovalErrorCode.TASK_NOT_FOUND, "Approval task not found"));
		return requestRepository.findByIdForUpdate(approvalId).orElseThrow();
	}

	private static ApprovalTask requireDecidable(ApprovalRequest approval, UUID taskId, UUID userId) {
		ApprovalTask task = approval.getTasks().stream().filter(candidate -> candidate.getId().equals(taskId))
				.findFirst().orElseThrow();
		if (!task.isAssignee(userId)) {
			throw new ApiException(ApprovalErrorCode.NOT_ASSIGNED, "This approval step is not assigned to you");
		}
		if (task.getStatus() != ApprovalTaskStatus.PENDING) {
			throw new ApiException(ApprovalErrorCode.TASK_NOT_PENDING,
					"This approval step is " + task.getStatus().name().toLowerCase() + ", not awaiting a decision");
		}
		return task;
	}

	private void publishOutcome(ApprovalRequest approval, Optional<ApprovalTask> nextStep, UUID decidedBy) {
		UUID tenantId = TenantContext.require().id();
		if (nextStep.isPresent()) {
			events.publishEvent(new ApprovalTaskAssigned(tenantId, approval.getId(), nextStep.get().getId(),
					approval.getSubjectType(), approval.getSubjectId(), Set.copyOf(nextStep.get().getAssigneeUserIds())));
		}
		else if (approval.getStatus() == ApprovalStatus.APPROVED) {
			events.publishEvent(new ApprovalCompleted(tenantId, approval.getId(), approval.getSubjectType(),
					approval.getSubjectId(), decidedBy));
		}
	}

	private Instant now() {
		return Instant.now(clock);
	}

	private static String blankToNull(String comment) {
		return comment == null || comment.isBlank() ? null : comment.trim();
	}

}
