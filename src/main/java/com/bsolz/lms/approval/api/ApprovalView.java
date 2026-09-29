package com.bsolz.lms.approval.api;

import com.bsolz.lms.approval.model.enums.ApprovalStatus;
import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An approval and its steps, for display. */
public record ApprovalView(UUID id, ApprovalSubjectType subjectType, UUID subjectId, String workflowName,
		ApprovalStatus status, Instant createdAt, Instant completedAt, List<ApprovalStepView> steps) {
}
