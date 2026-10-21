package com.bsolz.lms.identity.web.dto;

import com.bsolz.lms.identity.model.enums.UserStatus;
import java.util.Set;
import java.util.UUID;

/** {@code subject} is what {@code POST /local/tokens/tenant} takes. */
public record LocalUserResponse(UUID id, String email, String subject, UserStatus status, Set<String> roles) {
}
