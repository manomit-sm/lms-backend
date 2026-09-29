package com.bsolz.lms.approval.api;

import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Published when an overdue step gets additional approvers.
 *
 * @param addedUserIds the approvers added: the current approvers' managers, else the HR admins
 * @param pendingSince when the step became current
 */
public record ApprovalTaskEscalated(UUID tenantId, UUID approvalId, UUID taskId, ApprovalSubjectType subjectType,
		UUID subjectId, UUID requesterEmployeeId, Set<UUID> addedUserIds, Instant pendingSince, Instant escalatedAt) {
}
