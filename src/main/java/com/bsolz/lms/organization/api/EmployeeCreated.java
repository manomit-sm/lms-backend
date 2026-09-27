package com.bsolz.lms.organization.api;

import java.util.UUID;

/** Published when an employee is created; identity invites them as a user. */
public record EmployeeCreated(UUID tenantId, UUID employeeId, String email) {
}
