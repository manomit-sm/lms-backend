package com.bsolz.lms.identity.web.dto;

import com.bsolz.lms.identity.model.enums.UserStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id, String email, UserStatus status, String idpSubject, UUID employeeId,
		Set<String> roles, Instant activatedAt, Instant createdAt, Instant updatedAt) {
}
