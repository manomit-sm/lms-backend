package com.bsolz.lms.leave.api;

import com.bsolz.lms.leave.model.enums.LeaveAction;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import java.util.UUID;

/**
 * Published on every status change of a leave request, including submission ({@code from} null).
 *
 * @param actorUserId who caused it; null when it happened automatically
 */
public record LeaveRequestStatusChanged(UUID tenantId, UUID leaveRequestId, UUID employeeId, UUID leaveTypeId,
		LeaveStatus from, LeaveStatus to, LeaveAction action, UUID actorUserId) {
}
