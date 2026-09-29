package com.bsolz.lms.calendar.web.dto;

import com.bsolz.lms.calendar.model.enums.AvailabilityStatus;
import com.bsolz.lms.calendar.model.enums.CalendarScope;
import com.bsolz.lms.leave.model.enums.DaySession;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who is available on a date. Only approved leave counts.
 *
 * @param counts how many members have each status (every status is present)
 */
public record AvailabilityResponse(LocalDate date, CalendarScope scope, Map<AvailabilityStatus, Integer> counts,
		List<EmployeeAvailability> employees) {

	/**
	 * @param session the half on leave, for HALF_DAY_LEAVE
	 * @param leaveType the leave's type, only for members the caller may see in detail
	 * @param holidayName for HOLIDAY
	 */
	public record EmployeeAvailability(UUID employeeId, String name, AvailabilityStatus status, DaySession session,
			CalendarResponse.LeaveTypeRef leaveType, String holidayName) {
	}

}
