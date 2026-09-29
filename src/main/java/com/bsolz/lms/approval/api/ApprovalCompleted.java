package com.bsolz.lms.approval.api;

import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import java.util.UUID;

/**
 * Published when the last step is approved, or when no step had an approver. Delivered synchronously
 * inside the approving transaction too, so the subject's owner can update it atomically.
 *
 * @param approvedByUserId who approved the last step; null when the approval completed automatically
 * @param note why it completed automatically; null otherwise
 */
public record ApprovalCompleted(UUID tenantId, UUID approvalId, ApprovalSubjectType subjectType, UUID subjectId,
		UUID approvedByUserId, String note) {
}
