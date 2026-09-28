package com.bsolz.lms.holiday.web.dto;

import com.bsolz.lms.holiday.model.enums.HolidayType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Empty {@code locationIds}/{@code departmentIds} mean every location/department. */
public record HolidayResponse(UUID id, String name, LocalDate date, HolidayType type, String description,
		Set<UUID> locationIds, Set<UUID> departmentIds, Instant createdAt, Instant updatedAt) {
}
