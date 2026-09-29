package com.bsolz.lms.organization.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published when an employee leaves; identity disables their user.
 *
 * @param actorUserId who recorded the exit; null when not done by a user
 */
public record EmployeeExited(UUID tenantId, UUID employeeId, LocalDate exitDate, UUID actorUserId) {
}
