package com.bsolz.lms.platform.service;

import com.bsolz.lms.platform.entity.Tenant;
import com.bsolz.lms.platform.repository.TenantRepository;
import com.bsolz.lms.shared.tenancy.SchemaMigrationState;
import com.bsolz.lms.shared.tenancy.TenantInfo;
import com.bsolz.lms.shared.tenancy.TenantRegistry;
import com.bsolz.lms.shared.tenancy.TenantStatus;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link TenantRegistry} backed by {@code public.tenant}, with a short-lived cache because every
 * {@code /api/**} request resolves its tenant here. Changes made through this instance evict
 * immediately; other instances pick them up within {@link #CACHE_TTL}.
 */
@Service
@RequiredArgsConstructor
public class TenantRegistryService implements TenantRegistry {

	static final Duration CACHE_TTL = Duration.ofSeconds(30);

	private final TenantRepository tenantRepository;

	private final Cache<UUID, Optional<TenantInfo>> cache = Caffeine.newBuilder()
			.expireAfterWrite(CACHE_TTL)
			.maximumSize(10_000)
			.build();

	@Override
	public Optional<TenantInfo> findById(UUID tenantId) {
		return cache.get(tenantId, id -> tenantRepository.findById(id).map(Tenant::toInfo));
	}

	@Override
	public List<TenantInfo> findAllServing() {
		return tenantRepository.findAllByStatusAndMigrationState(TenantStatus.ACTIVE, SchemaMigrationState.UP_TO_DATE)
				.stream()
				.map(Tenant::toInfo)
				.toList();
	}

	public void evict(UUID tenantId) {
		cache.invalidate(tenantId);
	}

}
