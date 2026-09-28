package com.bsolz.lms.holiday.model.enums;

public enum HolidayType {

	/** A public (national/regional) holiday: a day off. */
	PUBLIC,

	/** A company-declared holiday: a day off. */
	COMPANY,

	/** A restricted/floating holiday: a working day unless the employee takes it as leave. */
	OPTIONAL;

	public boolean isDayOff() {
		return this != OPTIONAL;
	}

}
