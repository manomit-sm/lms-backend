package com.bsolz.lms.approval.api;

import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import java.time.Instant;
import java.util.UUID;

/** A task waiting for a user's decision. */
public record PendingTask(UUID taskId, UUID approvalId, ApprovalSubjectType subjectType, UUID subjectId,
		int stepOrder, String approverDescription, Instant assignedAt) {
}
