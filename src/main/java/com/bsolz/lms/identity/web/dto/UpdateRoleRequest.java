package com.bsolz.lms.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** The role code is permanent; everything else can change. */
public record UpdateRoleRequest(@NotBlank @Size(max = 100) String name, @Size(max = 255) String description,
		@NotEmpty Set<String> permissionCodes) {
}
