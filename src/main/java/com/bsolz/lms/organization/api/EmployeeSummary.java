package com.bsolz.lms.organization.api;

import com.bsolz.lms.organization.model.enums.EmploymentStatus;
import com.bsolz.lms.organization.model.enums.EmploymentType;
import java.time.LocalDate;
import java.util.UUID;

/** Read-only view of an employee for other modules. */
public record EmployeeSummary(UUID id, String employeeCode, String firstName, String lastName, String email,
		UUID departmentId, UUID designationId, UUID locationId, UUID reportingManagerId, UUID workScheduleId,
		EmploymentType employmentType, EmploymentStatus employmentStatus, LocalDate joiningDate, LocalDate exitDate) {

	public String fullName() {
		return firstName + " " + lastName;
	}

}
