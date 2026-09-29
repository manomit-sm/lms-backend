package com.bsolz.lms.approval.entity;

import com.bsolz.lms.approval.model.enums.ApproverType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;

/**
 * @param roleCode set for ROLE steps only
 * @param employeeId set for EMPLOYEE steps only
 */
@Embeddable
public record WorkflowStep(@Enumerated(EnumType.STRING) ApproverType approverType, String roleCode, UUID employeeId) {
}
