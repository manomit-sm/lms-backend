package com.bsolz.lms.approval.api;

import com.bsolz.lms.approval.model.enums.ApprovalStatus;
import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import com.bsolz.lms.approval.model.enums.ApprovalTaskStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a step is approved or rejected, for the audit trail.
 *
 * @param decision APPROVED or REJECTED
 * @param decidedByUserId null when approved automatically after its deadline
 * @param approvalStatus the approval's status after the decision: PENDING while further steps remain
 */
public record ApprovalTaskDecided(UUID tenantId, UUID approvalId, UUID taskId, ApprovalSubjectType subjectType,
		UUID subjectId, UUID requesterEmployeeId, int stepOrder, ApprovalTaskStatus decision, UUID decidedByUserId,
		String comment, ApprovalStatus approvalStatus, Instant decidedAt) {
}
