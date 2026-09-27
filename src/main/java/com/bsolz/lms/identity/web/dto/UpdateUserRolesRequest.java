package com.bsolz.lms.identity.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UpdateUserRolesRequest(@NotEmpty Set<String> roleCodes) {
}
