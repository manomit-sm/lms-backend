package com.bsolz.lms.organization.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** @param active defaults to true */
public record DepartmentRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 150) String name,
		UUID parentDepartmentId, UUID headEmployeeId, Boolean active) {
}
