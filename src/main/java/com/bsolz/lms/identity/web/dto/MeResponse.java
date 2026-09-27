package com.bsolz.lms.identity.web.dto;

import com.bsolz.lms.identity.model.enums.UserStatus;
import com.bsolz.lms.organization.api.EmployeeSummary;
import java.util.Set;
import java.util.UUID;

/**
 * Who the caller is: used by the frontend to build navigation from roles and permissions.
 *
 * @param employee null when the user has no employee record (e.g. a pure administrator)
 */
public record MeResponse(UUID userId, String email, UserStatus status, Tenant tenant, Set<String> roles,
		Set<String> permissions, EmployeeSummary employee) {

	public record Tenant(UUID id, String key) {
	}

}
