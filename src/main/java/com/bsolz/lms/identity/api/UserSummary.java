package com.bsolz.lms.identity.api;

import java.util.UUID;

/**
 * A tenant user as seen by other modules.
 *
 * @param employeeId the linked employee, or null for users without one (e.g. the first tenant admin)
 */
public record UserSummary(UUID id, String email, UUID employeeId, boolean enabled) {
}
