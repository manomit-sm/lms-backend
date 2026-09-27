package com.bsolz.lms.organization.web.dto;

import com.bsolz.lms.organization.model.enums.EmploymentStatus;
import com.bsolz.lms.organization.model.enums.EmploymentType;
import com.bsolz.lms.organization.model.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Create or full update. {@code employmentStatus} may not be {@code EXITED} - use the exit operation. */
public record EmployeeRequest(@NotBlank @Size(max = 40) String employeeCode,
		@NotBlank @Size(max = 100) String firstName, @NotBlank @Size(max = 100) String lastName,
		@NotBlank @Email @Size(max = 254) String email, @Size(max = 40) String phone, Gender gender,
		@NotNull UUID departmentId, UUID designationId, UUID locationId, UUID reportingManagerId,
		UUID workScheduleId, @NotNull EmploymentType employmentType, @NotNull EmploymentStatus employmentStatus,
		@NotNull LocalDate joiningDate, LocalDate probationEndDate) {
}
