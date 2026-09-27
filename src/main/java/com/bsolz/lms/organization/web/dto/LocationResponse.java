package com.bsolz.lms.organization.web.dto;

import java.time.Instant;
import java.util.UUID;

public record LocationResponse(UUID id, String code, String name, String countryCode, String timezone,
		UUID workScheduleId, boolean active, Instant createdAt, Instant updatedAt) {
}
