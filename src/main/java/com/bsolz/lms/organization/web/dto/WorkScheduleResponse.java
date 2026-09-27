package com.bsolz.lms.organization.web.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record WorkScheduleResponse(UUID id, String name, Set<DayOfWeek> workingDays, boolean defaultSchedule,
		Instant createdAt, Instant updatedAt) {
}
