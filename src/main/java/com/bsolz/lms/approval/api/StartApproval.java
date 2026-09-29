package com.bsolz.lms.approval.api;

import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * What the workflow needs to know to pick a workflow and resolve approvers.
 *
 * @param leaveTypeId matched against workflow rules
 * @param days matched against workflow rules' minimum days
 */
public record StartApproval(ApprovalSubjectType subjectType, UUID subjectId, UUID requesterEmployeeId,
		UUID requesterUserId, UUID leaveTypeId, BigDecimal days) {
}
