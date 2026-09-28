package com.bsolz.lms.holiday.web.dto;

import com.bsolz.lms.holiday.model.enums.HolidayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * @param type defaults to PUBLIC
 * @param locationIds limit the holiday to these locations; empty or absent: every location
 * @param departmentIds limit the holiday to these departments; empty or absent: every department
 */
public record HolidayRequest(@NotBlank @Size(max = 150) String name, @NotNull LocalDate date, HolidayType type,
		@Size(max = 500) String description, @Size(max = 200) Set<UUID> locationIds,
		@Size(max = 200) Set<UUID> departmentIds) {
}
