package com.bsolz.lms.settings.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;

/**
 * @param timezone IANA zone id; null to use the tenant's default timezone
 * @param dateFormat one of {@code SettingsService.DATE_FORMATS}
 */
public record SettingsRequest(@NotNull @Min(1) @Max(12) Integer leaveYearStartMonth, String timezone,
		@NotBlank String dateFormat, @NotNull DayOfWeek weekStartDay, @Size(max = 150) String organizationName,
		@Size(max = 500) @Pattern(regexp = "^https://\\S+$", message = "must be an https URL") String logoUrl) {
}
