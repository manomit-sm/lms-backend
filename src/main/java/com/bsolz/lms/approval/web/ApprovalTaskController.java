package com.bsolz.lms.approval.web;

import com.bsolz.lms.approval.api.ApprovalView;
import com.bsolz.lms.approval.api.PendingTask;
import com.bsolz.lms.approval.service.ApprovalService;
import com.bsolz.lms.approval.web.dto.DecisionRequest;
import com.bsolz.lms.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current user's approval tasks. Being an assignee is the authorization: approvers are resolved from
 * the org structure and roles when the approval starts. For leave details with the task, see
 * {@code GET /api/v1/leave-requests/pending-approval}.
 */
@RestController
@RequestMapping("/api/v1/approvals/tasks")
@RequiredArgsConstructor
class ApprovalTaskController {

	private final ApprovalService service;

	@GetMapping
	List<PendingTask> myPendingTasks() {
		return service.findPendingTasks(CurrentUser.require().userId());
	}

	@PostMapping("/{taskId}/approve")
	ApprovalView approve(@PathVariable UUID taskId, @Valid @RequestBody(required = false) DecisionRequest request) {
		return service.approve(taskId, request == null ? null : request.comment());
	}

	@PostMapping("/{taskId}/reject")
	ApprovalView reject(@PathVariable UUID taskId, @Valid @RequestBody DecisionRequest request) {
		return service.reject(taskId, request.comment());
	}

}
