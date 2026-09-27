package com.bsolz.lms.organization.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.util.Set;

public record WorkScheduleRequest(@NotBlank @Size(max = 100) String name, @NotEmpty Set<DayOfWeek> workingDays,
		boolean defaultSchedule) {
}
