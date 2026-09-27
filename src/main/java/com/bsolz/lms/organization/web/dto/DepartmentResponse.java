package com.bsolz.lms.organization.web.dto;

import java.time.Instant;
import java.util.UUID;

public record DepartmentResponse(UUID id, String code, String name, UUID parentDepartmentId, Reference head,
		boolean active, Instant createdAt, Instant updatedAt) {
}
