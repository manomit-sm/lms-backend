package com.bsolz.lms.leave.api;

import com.bsolz.lms.leave.model.enums.DaySession;
import com.bsolz.lms.leave.model.enums.LeaveDayType;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A leave request as seen by other modules, with its per-day breakdown.
 *
 * @param endDate inclusive
 */
public record LeaveSummary(UUID id, UUID employeeId, UUID leaveTypeId, LocalDate startDate, LocalDate endDate,
		DaySession startSession, DaySession endSession, BigDecimal totalDays, LeaveStatus status, List<Day> days) {

	/** The request's day on the date, if it covers it. */
	public Optional<Day> dayOn(LocalDate date) {
		return days.stream().filter(day -> day.date().equals(date)).findFirst();
	}

	/**
	 * @param amount the leave days drawn: 1 or 0.5 on a working day, 0 on a weekend or holiday unless the
	 * sandwich rule counts it
	 */
	public record Day(LocalDate date, LeaveDayType dayType, DaySession session, BigDecimal amount) {
	}

}
