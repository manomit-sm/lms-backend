package com.bsolz.lms.settings.web.dto;

import java.time.DayOfWeek;
import java.time.Instant;

/**
 * @param timezone the configured timezone, or null when the tenant's default applies
 * @param effectiveTimezone the timezone actually used
 */
public record SettingsResponse(int leaveYearStartMonth, String timezone, String effectiveTimezone, String dateFormat,
		DayOfWeek weekStartDay, String organizationName, String logoUrl, Instant updatedAt) {
}
