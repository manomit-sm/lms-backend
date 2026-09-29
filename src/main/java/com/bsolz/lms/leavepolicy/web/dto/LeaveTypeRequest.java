package com.bsolz.lms.leavepolicy.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param code letters, digits and underscores, e.g. {@code ANNUAL}; stored upper case
 * @param color {@code #RRGGBB}
 * @param balanceTracked false for types never limited by a balance (e.g. unpaid leave, work from home)
 * @param timeOff whether taking it means being away from work (false for e.g. work from home); defaults to
 * true. Calendars show employees on non-time-off leave as available.
 * @param halfDayAllowed defaults to true
 * @param active defaults to true
 */
public record LeaveTypeRequest(
		@NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,39}$", message = "must be 2-40 letters, digits or underscores") String code,
		@NotBlank @Size(max = 100) String name, @Size(max = 500) String description,
		@NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a #RRGGBB colour") String color,
		@NotNull Boolean paid, @NotNull Boolean balanceTracked, Boolean timeOff, Boolean halfDayAllowed,
		Boolean active, Integer sortOrder) {
}
