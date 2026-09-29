package com.bsolz.lms.leave.web.dto;

import com.bsolz.lms.leave.model.enums.LeaveAction;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import java.time.Instant;
import java.util.UUID;

/** @param actorUserId null when the change happened automatically */
public record HistoryEntry(LeaveStatus fromStatus, LeaveStatus toStatus, LeaveAction action, UUID actorUserId,
		String comment, Instant createdAt) {
}
