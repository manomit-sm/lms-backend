package com.bsolz.lms.approval.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param priority lower is tried first; unique
 * @param active defaults to true; the default workflow is always active
 * @param rules when the workflow applies (any rule); required, except for the default workflow which has none
 * @param steps the approvers, in order
 */
public record WorkflowRequest(@NotBlank @Size(max = 150) String name, @Size(max = 500) String description,
		@NotNull Integer priority, Boolean active, @Size(max = 20) List<@Valid WorkflowRuleDto> rules,
		@NotEmpty @Size(max = 5) List<@Valid WorkflowStepDto> steps) {
}
