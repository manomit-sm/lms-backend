package com.bsolz.lms.leave.web.dto;

import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import java.time.Instant;
import java.util.UUID;

/**
 * A leave request waiting for the current user's decision. Decide with
 * {@code POST /api/v1/approvals/tasks/{taskId}/approve} or {@code /reject}.
 *
 * @param subjectType LEAVE_REQUEST, or LEAVE_CANCELLATION when the decision is about cancelling it
 */
public record PendingApprovalResponse(UUID taskId, ApprovalSubjectType subjectType, int stepOrder,
		String approverDescription, Instant assignedAt, LeaveRequestSummary leaveRequest) {
}
