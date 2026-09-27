package com.bsolz.lms.identity.idp;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param provider {@code cognito} (deployed environments) or {@code fake} (local development, tests)
 */
@Validated
@ConfigurationProperties("lms.identity")
public record IdentityProperties(@NotBlank String provider, Cognito cognito) {

	public record Cognito(String userPoolId, String region) {
	}

}
