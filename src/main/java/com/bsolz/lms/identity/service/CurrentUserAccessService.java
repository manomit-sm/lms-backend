package com.bsolz.lms.identity.service;

import com.bsolz.lms.identity.entity.AppUser;
import com.bsolz.lms.identity.repository.AppUserRepository;
import com.bsolz.lms.shared.security.CurrentUserLoader;
import com.bsolz.lms.shared.security.UserAccess;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Resolves the signed-in subject to a user with roles and permissions, for every {@code /api/**}
 * request. Cached per tenant and subject because the same answer is needed on almost every request.
 * <p>
 * Any access change in a tenant publishes {@link UserAccessChanged}; the tenant's entries are cleared
 * <em>after</em> that transaction commits (clearing earlier would let a concurrent request re-cache
 * the old state). The cache is per instance: other instances pick up a change within
 * {@link #CACHE_TTL}. The first successful sign-in moves an invited user to ACTIVE.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserAccessService implements CurrentUserLoader {

	static final Duration CACHE_TTL = Duration.ofSeconds(60);

	private final AppUserRepository userRepository;

	private final TransactionTemplate transactionTemplate;

	private final Clock clock;

	private final Cache<AccessKey, Optional<UserAccess>> cache = Caffeine.newBuilder()
			.expireAfterWrite(CACHE_TTL)
			.maximumSize(50_000)
			.build();

	@Override
	public Optional<UserAccess> loadBySubject(String subject) {
		AccessKey key = new AccessKey(TenantContext.require().id(), subject);
		return cache.get(key, ignored -> transactionTemplate.execute(status -> load(subject)));
	}

	@TransactionalEventListener
	void on(UserAccessChanged event) {
		evictTenant(event.tenantId());
	}

	public void evictTenant(UUID tenantId) {
		cache.asMap().keySet().removeIf(key -> key.tenantId().equals(tenantId));
	}

	private Optional<UserAccess> load(String subject) {
		return userRepository.findByIdpSubject(subject).map(user -> {
			user.activate(Instant.now(clock));
			return toAccess(user);
		});
	}

	private static UserAccess toAccess(AppUser user) {
		return new UserAccess(user.getId(), user.getEmployeeId(), !user.isDisabled(), user.getRoleCodes(),
				user.getPermissionCodes());
	}

	private record AccessKey(UUID tenantId, String subject) {
	}

}
