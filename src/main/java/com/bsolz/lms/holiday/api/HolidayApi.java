package com.bsolz.lms.holiday.api;

import com.bsolz.lms.organization.api.EmployeeSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Holidays for other modules. All calls need a tenant bound. */
public interface HolidayApi {

	/**
	 * Holidays that apply to the employee (by location and department) between the dates, inclusive,
	 * ordered by date. Includes optional holidays; see {@link HolidayInfo#isDayOff()}.
	 */
	List<HolidayInfo> findHolidaysFor(EmployeeSummary employee, LocalDate from, LocalDate to);

	/** As {@link #findHolidaysFor(EmployeeSummary, LocalDate, LocalDate)}; empty for an unknown employee. */
	List<HolidayInfo> findHolidaysFor(UUID employeeId, LocalDate from, LocalDate to);

}
