package com.bsolz.lms.identity.api;

import java.util.UUID;

/** Identity operations for other modules. Calls need the target tenant bound. */
public interface IdentityApi {

	/**
	 * Makes {@code email} a tenant admin: creates the user if needed, grants TENANT_ADMIN and sends
	 * the identity-provider invitation. Idempotent.
	 *
	 * @return the user id
	 */
	UUID ensureTenantAdmin(String email);

}
