package com.bsolz.lms.shared.security;

/** Claim names in access tokens (Cognito access-token layout plus our {@code tenant_id}). */
public final class LmsClaims {

	/** Tenant id, added to tenant-pool access tokens by the Cognito Pre Token Generation Lambda. */
	public static final String TENANT_ID = "tenant_id";

	/** {@code access} for access tokens; ID tokens are rejected. */
	public static final String TOKEN_USE = "token_use";

	public static final String CLIENT_ID = "client_id";

	public static final String GROUPS = "cognito:groups";

	/** Cognito group in the platform user pool whose members may call {@code /platform/**}. */
	public static final String PLATFORM_ADMIN_GROUP = "platform-admins";

	private LmsClaims() {
	}

}
