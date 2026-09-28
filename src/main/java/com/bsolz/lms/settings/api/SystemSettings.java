package com.bsolz.lms.settings.api;

import java.time.DayOfWeek;
import java.time.ZoneId;

/**
 * The tenant's effective settings.
 *
 * @param leaveYearStartMonth 1 (January) to 12; new leave periods start on the first of this month
 * @param timezone the tenant's timezone: the configured one, otherwise the tenant's default
 * @param dateFormat display pattern for the frontend, e.g. {@code dd/MM/yyyy}
 */
public record SystemSettings(int leaveYearStartMonth, ZoneId timezone, String dateFormat, DayOfWeek weekStartDay) {
}
