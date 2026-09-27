package com.bsolz.lms.identity.idp;

import com.bsolz.lms.identity.exception.IdentityErrorCode;
import com.bsolz.lms.shared.exception.ApiException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory identity provider for local development and tests: same contract and idempotency as
 * Cognito, no invitation emails. Subjects are random UUIDs - use them with the local token issuer
 * to sign in as the user. State is lost on restart (users in the database keep their subjects).
 */
@Slf4j
public class FakeIdentityProvider implements IdentityProviderClient {

	private final Map<String, FakeUser> users = new ConcurrentHashMap<>();

	@Override
	public String inviteUser(String email, UUID tenantId) {
		FakeUser user = users.computeIfAbsent(key(email), ignored -> {
			FakeUser created = new FakeUser(UUID.randomUUID().toString(), tenantId, true);
			log.info("Fake identity provider: invited {} (sub={})", email, created.subject());
			return created;
		});
		if (!user.tenantId().equals(tenantId)) {
			throw new ApiException(IdentityErrorCode.EMAIL_REGISTERED_ELSEWHERE,
					"Email " + email + " is already registered with another organisation");
		}
		return user.subject();
	}

	@Override
	public void disableUser(String email) {
		users.computeIfPresent(key(email), (ignored, user) -> new FakeUser(user.subject(), user.tenantId(), false));
	}

	@Override
	public void enableUser(String email) {
		users.computeIfPresent(key(email), (ignored, user) -> new FakeUser(user.subject(), user.tenantId(), true));
	}

	public Optional<FakeUser> find(String email) {
		return Optional.ofNullable(users.get(key(email)));
	}

	private static String key(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	public record FakeUser(String subject, UUID tenantId, boolean enabled) {
	}

}
