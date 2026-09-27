package com.bsolz.lms.organization.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** @param active defaults to true */
public record DesignationRequest(@NotBlank @Size(max = 150) String name, @PositiveOrZero Integer level,
		Boolean active) {
}
