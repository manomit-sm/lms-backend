package com.bsolz.lms.shared.security;

import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

/** Token checks on top of signature/expiry/issuer: access token, known app client, tenant claim. */
final class LmsJwtValidators {

	private LmsJwtValidators() {
	}

	/** Tenant realm: must carry a UUID {@code tenant_id}. */
	static OAuth2TokenValidator<Jwt> forTenant(SecurityProperties.Realm realm) {
		return new DelegatingOAuth2TokenValidator<>(common(realm), new JwtClaimValidator<Object>(LmsClaims.TENANT_ID,
				value -> value != null && isUuid(value.toString())));
	}

	/** Platform realm: must NOT carry a {@code tenant_id} - platform and tenant tokens are never interchangeable. */
	static OAuth2TokenValidator<Jwt> forPlatform(SecurityProperties.Realm realm) {
		OAuth2TokenValidator<Jwt> noTenantClaim = jwt -> jwt.hasClaim(LmsClaims.TENANT_ID)
				? OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
						"Platform tokens must not carry a tenant", null))
				: OAuth2TokenValidatorResult.success();
		return new DelegatingOAuth2TokenValidator<>(common(realm), noTenantClaim);
	}

	private static OAuth2TokenValidator<Jwt> common(SecurityProperties.Realm realm) {
		List<String> clientIds = realm.clientIds();
		return new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(realm.issuerUri().toString()),
				new JwtClaimValidator<String>(LmsClaims.TOKEN_USE, "access"::equals),
				new JwtClaimValidator<String>(LmsClaims.CLIENT_ID, clientIds::contains));
	}

	private static boolean isUuid(String value) {
		try {
			UUID.fromString(value);
			return true;
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

}
