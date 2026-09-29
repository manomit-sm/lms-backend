package com.bsolz.lms.leave.web.dto;

import com.bsolz.lms.leave.model.enums.DaySession;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param endDate inclusive
 * @param startSession defaults to FULL_DAY; SECOND_HALF starts at midday
 * @param endSession defaults to the start session for a single day, else FULL_DAY; FIRST_HALF ends at midday
 * @param attachmentIds uploaded with {@code POST /api/v1/leave-requests/attachments}
 */
public record LeaveApplicationRequest(@NotNull UUID leaveTypeId, @NotNull LocalDate startDate,
		@NotNull LocalDate endDate, DaySession startSession, DaySession endSession, @Size(max = 1000) String reason,
		@Size(max = 5) List<UUID> attachmentIds) {
}
