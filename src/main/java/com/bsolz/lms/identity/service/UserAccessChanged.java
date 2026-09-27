package com.bsolz.lms.identity.service;

import com.bsolz.lms.shared.tenancy.TenantContext;
import java.util.UUID;

/**
 * Something affecting users' access in the tenant changed (roles, role permissions, user status).
 * Internal to identity: {@link CurrentUserAccessService} clears the tenant's cached access once the
 * change has committed.
 */
public record UserAccessChanged(UUID tenantId) {

	static UserAccessChanged inCurrentTenant() {
		return new UserAccessChanged(TenantContext.require().id());
	}

}
