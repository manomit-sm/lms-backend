package com.bsolz.lms.holiday.api;

import com.bsolz.lms.holiday.model.enums.HolidayType;
import java.time.LocalDate;
import java.util.UUID;

/** A holiday as seen by other modules. */
public record HolidayInfo(UUID id, String name, LocalDate date, HolidayType type) {

	/** False for optional holidays, which are working days unless taken as leave. */
	public boolean isDayOff() {
		return type.isDayOff();
	}

}
