package com.bsolz.lms.shared.security;

import java.util.Set;
import java.util.UUID;

/**
 * A tenant user's access, as provided by {@link CurrentUserLoader}.
 *
 * @param employeeId the linked employee, or null for users without an employee record
 */
public record UserAccess(UUID userId, UUID employeeId, boolean active, Set<String> roles, Set<String> permissions) {
}
