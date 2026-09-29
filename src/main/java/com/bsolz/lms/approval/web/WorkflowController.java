package com.bsolz.lms.approval.web;

import com.bsolz.lms.approval.service.WorkflowService;
import com.bsolz.lms.approval.web.dto.WorkflowRequest;
import com.bsolz.lms.approval.web.dto.WorkflowResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Approval workflow administration (WORKFLOW_MANAGE). Deactivate workflows instead of deleting them. */
@RestController
@RequestMapping("/api/v1/approval-workflows")
@PreAuthorize("hasAuthority('WORKFLOW_MANAGE')")
@RequiredArgsConstructor
class WorkflowController {

	private final WorkflowService service;

	@GetMapping
	List<WorkflowResponse> list() {
		return service.list();
	}

	@GetMapping("/{workflowId}")
	WorkflowResponse get(@PathVariable UUID workflowId) {
		return service.get(workflowId);
	}

	@PostMapping
	ResponseEntity<WorkflowResponse> create(@Valid @RequestBody WorkflowRequest request) {
		WorkflowResponse created = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/approval-workflows/" + created.id())).body(created);
	}

	@PutMapping("/{workflowId}")
	WorkflowResponse update(@PathVariable UUID workflowId, @Valid @RequestBody WorkflowRequest request) {
		return service.update(workflowId, request);
	}

}
