package com.bsolz.lms.approval.web.dto;

import com.bsolz.lms.approval.model.enums.ApproverType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * @param roleCode required for ROLE, not allowed otherwise
 * @param employeeId required for EMPLOYEE, not allowed otherwise
 */
public record WorkflowStepDto(@NotNull ApproverType approverType, String roleCode, UUID employeeId) {
}
