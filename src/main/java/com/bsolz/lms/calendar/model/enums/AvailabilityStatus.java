package com.bsolz.lms.calendar.model.enums;

/** An employee's availability on a date, from approved leave, holidays and their work schedule. */
public enum AvailabilityStatus {

	AVAILABLE,

	/** On leave that isn't time off, e.g. working from home. */
	REMOTE,

	/** On leave for half the day; see the session. */
	HALF_DAY_LEAVE,

	ON_LEAVE,

	/** A day-off holiday that applies to them. */
	HOLIDAY,

	/** Not a working day in their work schedule. */
	NON_WORKING_DAY

}
