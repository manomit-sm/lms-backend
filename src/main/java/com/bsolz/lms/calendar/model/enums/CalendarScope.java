package com.bsolz.lms.calendar.model.enums;

import com.bsolz.lms.calendar.exception.CalendarErrorCode;
import com.bsolz.lms.shared.exception.ApiException;
import java.util.Locale;

/** Whose leave a calendar shows. */
public enum CalendarScope {

	/** Only the caller. */
	ME,

	/** The caller, their manager, the manager's other direct reports and the caller's own direct reports. */
	TEAM,

	/** Everyone in a department: the caller's own, or any for callers who may see every employee. */
	DEPARTMENT;

	/** Parses {@code me}, {@code team} or {@code department}, in any case. */
	public static CalendarScope parse(String value) {
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new ApiException(CalendarErrorCode.INVALID_SCOPE, "scope must be me, team or department");
		}
	}

}
