package com.bsolz.lms.organization.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ExitEmployeeRequest(@NotNull LocalDate exitDate) {
}
