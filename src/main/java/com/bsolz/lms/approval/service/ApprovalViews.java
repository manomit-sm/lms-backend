package com.bsolz.lms.approval.service;

import com.bsolz.lms.approval.api.ApprovalStepView;
import com.bsolz.lms.approval.api.ApprovalView;
import com.bsolz.lms.approval.api.ApproverRef;
import com.bsolz.lms.approval.entity.ApprovalRequest;
import com.bsolz.lms.approval.entity.ApprovalTask;
import com.bsolz.lms.identity.api.IdentityApi;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds approval views with approvers' names (employee name, else the user's email). */
@Component
@RequiredArgsConstructor
class ApprovalViews {

	private final IdentityApi identityApi;

	List<ApprovalView> toViews(Collection<ApprovalRequest> approvals) {
		Set<UUID> userIds = new HashSet<>();
		approvals.forEach(approval -> approval.getTasks().forEach(task -> {
			userIds.addAll(task.getAssigneeUserIds());
			if (task.getActedByUserId() != null) {
				userIds.add(task.getActedByUserId());
			}
		}));
		Map<UUID, String> names = names(userIds);
		return approvals.stream().map(approval -> new ApprovalView(approval.getId(), approval.getSubjectType(),
				approval.getSubjectId(), approval.getWorkflowName(), approval.getStatus(), approval.getCreatedAt(),
				approval.getCompletedAt(), approval.getTasks().stream().map(task -> step(task, names)).toList()))
				.toList();
	}

	ApprovalView toView(ApprovalRequest approval) {
		return toViews(List.of(approval)).getFirst();
	}

	private static ApprovalStepView step(ApprovalTask task, Map<UUID, String> names) {
		List<ApproverRef> assignees = task.getAssigneeUserIds().stream()
				.map(userId -> ref(userId, names))
				.sorted(Comparator.comparing(ApproverRef::name))
				.toList();
		ApproverRef actedBy = task.getActedByUserId() == null ? null : ref(task.getActedByUserId(), names);
		return new ApprovalStepView(task.getId(), task.getStepOrder(), task.getApproverType(),
				task.getApproverDescription(), task.getStatus(), assignees, actedBy, task.getActedAt(), task.getComment());
	}

	private static ApproverRef ref(UUID userId, Map<UUID, String> names) {
		return new ApproverRef(userId, names.getOrDefault(userId, "Unknown user"));
	}

	private Map<UUID, String> names(Set<UUID> userIds) {
		return identityApi.findDisplayNames(userIds);
	}

}
