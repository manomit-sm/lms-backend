package com.bsolz.lms.approval.api;

import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import java.util.Set;
import java.util.UUID;

/** Published when a step becomes the current one, for notifying its assignees. */
public record ApprovalTaskAssigned(UUID tenantId, UUID approvalId, UUID taskId, ApprovalSubjectType subjectType,
		UUID subjectId, Set<UUID> assigneeUserIds) {
}
