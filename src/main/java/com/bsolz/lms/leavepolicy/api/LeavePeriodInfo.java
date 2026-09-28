package com.bsolz.lms.leavepolicy.api;

import com.bsolz.lms.leavepolicy.model.enums.LeavePeriodStatus;
import java.time.LocalDate;
import java.util.UUID;

/** A leave period (inclusive dates) as seen by other modules. */
public record LeavePeriodInfo(UUID id, String name, LocalDate startDate, LocalDate endDate, LeavePeriodStatus status) {

	public boolean contains(LocalDate date) {
		return !date.isBefore(startDate) && !date.isAfter(endDate);
	}

}
