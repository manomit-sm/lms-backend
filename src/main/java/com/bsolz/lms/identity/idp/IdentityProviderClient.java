package com.bsolz.lms.identity.idp;

import java.util.UUID;

/**
 * User lifecycle in the external identity provider (Cognito in deployed environments). Users are
 * addressed by email, which is the provider's username. Implementations must be idempotent: a
 * retried event must not fail because an earlier attempt already created the user.
 */
public interface IdentityProviderClient {

	/**
	 * Creates the user with {@code tenantId} as their tenant and sends the invitation; if the user
	 * already exists for the same tenant, returns them unchanged.
	 *
	 * @return the provider's subject ({@code sub}) for the user
	 * @throws com.bsolz.lms.shared.exception.ApiException {@code EMAIL_REGISTERED_ELSEWHERE} if the
	 * email belongs to another tenant
	 */
	String inviteUser(String email, UUID tenantId);

	/** Blocks sign-in and revokes the user's refresh tokens. */
	void disableUser(String email);

	void enableUser(String email);

}
