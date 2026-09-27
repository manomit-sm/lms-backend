package com.bsolz.lms.organization.service;

import com.bsolz.lms.organization.model.enums.EmploymentStatus;
import java.util.UUID;

/** Optional employee list filters; {@code search} matches name, email or employee code. */
public record EmployeeFilter(String search, UUID departmentId, UUID reportingManagerId, EmploymentStatus status) {
}
