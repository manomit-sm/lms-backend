package com.bsolz.lms.organization.api;

import java.time.LocalDate;
import java.util.UUID;

/** Published when an employee leaves; identity disables their user. */
public record EmployeeExited(UUID tenantId, UUID employeeId, LocalDate exitDate) {
}
