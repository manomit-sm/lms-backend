package com.bsolz.lms.organization.api;

import com.bsolz.lms.organization.model.enums.EmploymentStatus;
import com.bsolz.lms.organization.model.enums.EmploymentType;
import com.bsolz.lms.organization.model.enums.Gender;
import java.time.LocalDate;
import java.util.UUID;

/** Read-only view of an employee for other modules. */
public record EmployeeSummary(UUID id, String employeeCode, String firstName, String lastName, String email,
		Gender gender, UUID departmentId, UUID designationId, UUID locationId, UUID reportingManagerId,
		UUID workScheduleId, EmploymentType employmentType, EmploymentStatus employmentStatus, LocalDate joiningDate,
		LocalDate probationEndDate, LocalDate exitDate) {

	public String fullName() {
		return firstName + " " + lastName;
	}

	public boolean isExited() {
		return employmentStatus == EmploymentStatus.EXITED;
	}

}
