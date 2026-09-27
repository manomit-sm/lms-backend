package com.bsolz.lms.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateRoleRequest(
		@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,39}$",
				message = "must be 3-40 upper-case letters, digits or underscores") String code,
		@NotBlank @Size(max = 100) String name, @Size(max = 255) String description,
		@NotEmpty Set<String> permissionCodes) {
}
