package com.bsolz.lms.approval.api;

import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Published when a step has waited longer than its workflow's reminder interval, for reminding its
 * approvers.
 *
 * @param pendingSince when the step became current
 */
public record ApprovalTaskReminded(UUID tenantId, UUID approvalId, UUID taskId, ApprovalSubjectType subjectType,
		UUID subjectId, Set<UUID> assigneeUserIds, Instant pendingSince, Instant remindedAt) {
}
