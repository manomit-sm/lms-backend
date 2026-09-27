package com.bsolz.lms.shared.security.local;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local-development token endpoint (Postman/frontend), mirroring what Cognito will issue. */
@RestController
@RequestMapping("/local/tokens")
@RequiredArgsConstructor
@ConditionalOnBooleanProperty("lms.security.local-issuer.enabled")
class LocalTokenController {

	private final LocalTokenIssuer issuer;

	@PostMapping("/tenant")
	TokenResponse tenantToken(@Valid @RequestBody TenantTokenRequest request) {
		Duration ttl = Duration.ofMinutes(request.ttlMinutes());
		return new TokenResponse(issuer.issueTenantToken(request.subject(), request.tenantId(), ttl), ttl.toSeconds());
	}

	@PostMapping("/platform")
	TokenResponse platformToken(@Valid @RequestBody PlatformTokenRequest request) {
		Duration ttl = Duration.ofMinutes(request.ttlMinutes());
		return new TokenResponse(issuer.issuePlatformToken(request.subject(), true, ttl), ttl.toSeconds());
	}

	record TenantTokenRequest(@NotBlank String subject, @NotNull UUID tenantId, @Min(1) @Max(1440) int ttlMinutes) {
	}

	record PlatformTokenRequest(@NotBlank String subject, @Min(1) @Max(1440) int ttlMinutes) {
	}

	record TokenResponse(String accessToken, long expiresIn) {

		public String tokenType() {
			return "Bearer";
		}

	}

}
