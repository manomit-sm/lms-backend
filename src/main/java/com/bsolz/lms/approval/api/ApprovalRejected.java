package com.bsolz.lms.approval.api;

import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import java.util.UUID;

/** Published when a step is rejected; delivered synchronously inside the rejecting transaction too. */
public record ApprovalRejected(UUID tenantId, UUID approvalId, ApprovalSubjectType subjectType, UUID subjectId,
		UUID rejectedByUserId, String comment) {
}
