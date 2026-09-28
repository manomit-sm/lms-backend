package com.bsolz.lms.leavepolicy.web.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Leave both dates out to create the next leave year: it starts the day after the latest period ends
 * (or at the current leave year's start if there is none) and lasts twelve months.
 *
 * @param name defaults to the year, e.g. {@code 2027}, or {@code 2027-28} for a year spanning two
 * @param startDate the first of a month
 * @param endDate the last day of a month, at most twelve months after the start
 */
public record LeavePeriodRequest(@Size(max = 100) String name, LocalDate startDate, LocalDate endDate) {
}
