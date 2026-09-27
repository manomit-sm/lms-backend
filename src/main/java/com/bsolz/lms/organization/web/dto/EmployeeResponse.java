package com.bsolz.lms.organization.web.dto;

import com.bsolz.lms.organization.model.enums.EmploymentStatus;
import com.bsolz.lms.organization.model.enums.EmploymentType;
import com.bsolz.lms.organization.model.enums.Gender;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeResponse(UUID id, String employeeCode, String firstName, String lastName, String fullName,
		String email, String phone, Gender gender, Reference department, Reference designation, Reference location,
		Reference reportingManager, UUID workScheduleId, EmploymentType employmentType,
		EmploymentStatus employmentStatus, LocalDate joiningDate, LocalDate probationEndDate, LocalDate exitDate,
		Instant createdAt, Instant updatedAt) {
}
