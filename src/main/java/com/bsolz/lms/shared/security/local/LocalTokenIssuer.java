package com.bsolz.lms.shared.security.local;

import com.bsolz.lms.shared.security.LmsClaims;
import com.bsolz.lms.shared.security.SecurityProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Issues Cognito-shaped access tokens signed with an in-memory RSA key, so the API can be used
 * locally and in tests before Cognito exists. Claims mirror real Cognito access tokens
 * ({@code token_use}, {@code client_id}, {@code cognito:groups}) plus our {@code tenant_id}, so no
 * code changes when real tokens arrive. The key is regenerated on every start.
 * <p>
 * Only exists when {@code lms.security.local-issuer.enabled=true}; refuses to start under the
 * {@code prod} or {@code aws} profiles.
 */
@Slf4j
@Component
@ConditionalOnBooleanProperty("lms.security.local-issuer.enabled")
public class LocalTokenIssuer {

	private final SecurityProperties properties;

	private final Clock clock;

	private final RSAPublicKey publicKey;

	private final String keyId;

	private final JwtEncoder encoder;

	public LocalTokenIssuer(SecurityProperties properties, Clock clock, Environment environment) {
		if (environment.acceptsProfiles(Profiles.of("prod", "aws"))) {
			throw new IllegalStateException("The local token issuer must never be enabled in a deployed environment");
		}
		this.properties = properties;
		this.clock = clock;
		KeyPair keyPair = generateRsaKeyPair();
		this.publicKey = (RSAPublicKey) keyPair.getPublic();
		this.keyId = UUID.randomUUID().toString();
		RSAKey jwk = new RSAKey.Builder(publicKey).privateKey((RSAPrivateKey) keyPair.getPrivate()).keyID(keyId).build();
		this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
		log.warn("Local token issuer is ENABLED - tokens are self-signed; never use this outside local development/tests");
	}

	public RSAPublicKey publicKey() {
		return publicKey;
	}

	public String issueTenantToken(String subject, UUID tenantId, Duration ttl) {
		return issue(properties.tenant(), subject, Map.of(LmsClaims.TENANT_ID, tenantId.toString()), ttl);
	}

	public String issuePlatformToken(String subject, boolean platformAdmin, Duration ttl) {
		List<String> groups = platformAdmin ? List.of(LmsClaims.PLATFORM_ADMIN_GROUP) : List.of();
		return issue(properties.platform(), subject, Map.of(LmsClaims.GROUPS, groups), ttl);
	}

	/** Low-level issue for tests that need unusual claims; {@code extraClaims} override the defaults. */
	public String issue(SecurityProperties.Realm realm, String subject, Map<String, Object> extraClaims, Duration ttl) {
		Instant now = Instant.now(clock);
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer(realm.issuerUri().toString())
				.subject(subject)
				.issuedAt(now)
				.expiresAt(now.plus(ttl))
				.id(UUID.randomUUID().toString())
				.claim(LmsClaims.TOKEN_USE, "access")
				.claim(LmsClaims.CLIENT_ID, realm.clientIds().getFirst())
				.claim("username", subject);
		extraClaims.forEach(claims::claim);
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}

	private static KeyPair generateRsaKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("RSA is not available", ex);
		}
	}

}
