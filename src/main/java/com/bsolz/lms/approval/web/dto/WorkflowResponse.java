package com.bsolz.lms.approval.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code steps} in approval order. */
public record WorkflowResponse(UUID id, String name, String description, int priority, boolean defaultWorkflow,
		boolean active, List<WorkflowRuleDto> rules, List<WorkflowStepDto> steps, Instant createdAt,
		Instant updatedAt) {
}
