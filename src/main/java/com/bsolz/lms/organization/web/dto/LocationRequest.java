package com.bsolz.lms.organization.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * @param countryCode ISO 3166-1 alpha-2, upper case
 * @param timezone IANA zone id, e.g. {@code Asia/Kolkata}
 * @param active defaults to true
 */
public record LocationRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 150) String name,
		@NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "must be an ISO 3166-1 alpha-2 code") String countryCode,
		@NotBlank String timezone, UUID workScheduleId, Boolean active) {
}
