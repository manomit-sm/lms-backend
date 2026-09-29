package com.bsolz.lms.calendar.web.dto;

import com.bsolz.lms.calendar.model.enums.CalendarScope;
import com.bsolz.lms.holiday.model.enums.HolidayType;
import com.bsolz.lms.leave.model.enums.DaySession;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Leave and holidays between two dates.
 *
 * @param members everyone in the scope, by name
 * @param leaves leave overlapping the dates: pending, approved and awaiting cancellation for members the caller
 * may see (see {@code detailed}); only approved leave, without its type, for the others
 * @param holidays the caller's holidays (by their location and department)
 */
public record CalendarResponse(CalendarScope scope, LocalDate from, LocalDate to, List<Member> members,
		List<Leave> leaves, List<Holiday> holidays) {

	/** @param detailed whether the caller may see this member's leave in detail */
	public record Member(UUID id, String name, UUID departmentId, boolean detailed) {
	}

	/**
	 * @param id null unless the member is detailed
	 * @param leaveType null unless the member is detailed
	 * @param timeOff false for leave that isn't time off, e.g. working from home
	 * @param totalDays null unless the member is detailed
	 */
	public record Leave(UUID id, UUID employeeId, LeaveTypeRef leaveType, boolean timeOff, LocalDate startDate,
			LocalDate endDate, DaySession startSession, DaySession endSession, BigDecimal totalDays,
			LeaveStatus status) {
	}

	public record LeaveTypeRef(UUID id, String code, String name, String color) {
	}

	public record Holiday(UUID id, String name, LocalDate date, HolidayType type) {
	}

}
