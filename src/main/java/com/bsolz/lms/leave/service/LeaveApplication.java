package com.bsolz.lms.leave.service;

import com.bsolz.lms.leave.model.enums.DaySession;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What the employee asks for, with sessions already defaulted (see {@link #of}). */
public record LeaveApplication(UUID leaveTypeId, LocalDate startDate, LocalDate endDate, DaySession startSession,
		DaySession endSession, String reason, List<UUID> attachmentIds) {

	/**
	 * Defaults: the start session is a full day; the end session is the start session for a single day,
	 * otherwise a full day.
	 */
	public static LeaveApplication of(UUID leaveTypeId, LocalDate startDate, LocalDate endDate, DaySession startSession,
			DaySession endSession, String reason, List<UUID> attachmentIds) {
		DaySession start = startSession != null ? startSession : DaySession.FULL_DAY;
		DaySession end = endSession != null ? endSession : startDate.equals(endDate) ? start : DaySession.FULL_DAY;
		return new LeaveApplication(leaveTypeId, startDate, endDate, start, end,
				reason == null || reason.isBlank() ? null : reason.trim(),
				attachmentIds == null ? List.of() : List.copyOf(attachmentIds));
	}

}
