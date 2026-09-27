package com.bsolz.lms.shared.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Token issuers the API trusts: the tenant user pool (for {@code /api/**}) and the separate
 * platform-admin user pool (for {@code /platform/**}).
 *
 * @param tenant issuer of tenant users' access tokens
 * @param platform issuer of platform administrators' access tokens
 */
@Validated
@ConfigurationProperties("lms.security")
public record SecurityProperties(@Valid @NotNull Realm tenant, @Valid @NotNull Realm platform) {

	/**
	 * @param issuerUri expected {@code iss}; also used for OIDC discovery of the signing keys
	 * @param clientIds app client ids whose access tokens are accepted ({@code client_id} claim)
	 */
	public record Realm(@NotNull URI issuerUri, @NotEmpty List<String> clientIds) {
	}

}
