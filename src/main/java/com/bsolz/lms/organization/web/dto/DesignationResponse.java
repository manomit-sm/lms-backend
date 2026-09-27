package com.bsolz.lms.organization.web.dto;

import java.time.Instant;
import java.util.UUID;

public record DesignationResponse(UUID id, String name, Integer level, boolean active, Instant createdAt,
		Instant updatedAt) {
}
