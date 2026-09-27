package com.bsolz.lms.identity.web.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RoleResponse(UUID id, String code, String name, String description, boolean systemRole,
		Set<String> permissions, Instant createdAt, Instant updatedAt) {
}
