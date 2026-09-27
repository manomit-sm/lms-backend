package com.bsolz.lms.platform.service;

import com.bsolz.lms.identity.api.IdentityApi;
import com.bsolz.lms.platform.entity.Tenant;
import com.bsolz.lms.platform.exception.PlatformErrorCode;
import com.bsolz.lms.platform.repository.TenantRepository;
import com.bsolz.lms.platform.web.dto.CreateTenantRequest;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.tenancy.SchemaMigrationState;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.shared.tenancy.TenantInfo;
import com.bsolz.lms.shared.tenancy.TenantStatus;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Tenant lifecycle. Provisioning = register the tenant ({@code PROVISIONING}), create and migrate
 * its schema (it becomes {@code ACTIVE}), then invite the first tenant admin. Each step is
 * idempotent: a failed migration is completed with {@link #migrate(UUID)}, a failed admin invitation
 * with {@link #addAdmin(UUID, String)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

	private final TenantRepository tenantRepository;

	private final TenantMigrationService migrationService;

	private final TenantRegistryService tenantRegistry;

	private final IdentityApi identityApi;

	public Tenant provision(CreateTenantRequest request) {
		ZoneId timezone = parseTimezone(request.defaultTimezone());
		if (tenantRepository.existsByTenantKey(request.key())) {
			throw new ApiException(PlatformErrorCode.TENANT_KEY_TAKEN, "Tenant key '" + request.key() + "' is taken");
		}
		if (request.subdomain() != null && tenantRepository.existsBySubdomain(request.subdomain())) {
			throw new ApiException(PlatformErrorCode.SUBDOMAIN_TAKEN,
					"Subdomain '" + request.subdomain() + "' is taken");
		}
		Tenant tenant;
		try {
			tenant = tenantRepository.saveAndFlush(
					Tenant.provision(request.key(), request.name(), request.subdomain(), timezone));
		}
		catch (DataIntegrityViolationException ex) {
			// Lost a race with a concurrent request for the same key or subdomain.
			throw new ApiException(PlatformErrorCode.TENANT_KEY_TAKEN, "Tenant key or subdomain is taken");
		}
		log.info("Provisioning tenant {} ({})", tenant.getTenantKey(), tenant.getId());
		Tenant migrated = requireMigrated(migrationService.migrateTenant(tenant.getId()));
		try {
			addAdmin(migrated.getId(), request.adminEmail());
		}
		catch (RuntimeException ex) {
			log.error("Tenant {} is ready but inviting its admin failed", migrated.getTenantKey(), ex);
			throw new ApiException(PlatformErrorCode.TENANT_ADMIN_SETUP_FAILED, "Tenant " + migrated.getId()
					+ " was created but inviting its admin failed; retry POST /platform/tenants/" + migrated.getId()
					+ "/admins");
		}
		return migrated;
	}

	/** Makes {@code email} a tenant admin of the tenant (creating and inviting the user if needed). */
	public UUID addAdmin(UUID tenantId, String email) {
		TenantInfo tenant = get(tenantId).toInfo();
		if (tenant.migrationState() != SchemaMigrationState.UP_TO_DATE
				|| tenant.status() == TenantStatus.DEACTIVATED) {
			throw new ApiException(PlatformErrorCode.TENANT_NOT_SERVING,
					"Tenant schema must be up to date and the tenant not deactivated");
		}
		return TenantContext.call(tenant, () -> identityApi.ensureTenantAdmin(email));
	}

	/** Retries a failed provisioning or schema upgrade. */
	public Tenant migrate(UUID tenantId) {
		return requireMigrated(migrationService.migrateTenant(tenantId));
	}

	public Tenant suspend(UUID tenantId) {
		Tenant tenant = get(tenantId);
		requireStatus(tenant, TenantStatus.ACTIVE, "suspended");
		tenant.suspend();
		return saveAndEvict(tenant);
	}

	public Tenant activate(UUID tenantId) {
		Tenant tenant = get(tenantId);
		requireStatus(tenant, TenantStatus.SUSPENDED, "activated");
		tenant.activate();
		return saveAndEvict(tenant);
	}

	public Tenant get(UUID tenantId) {
		return tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ApiException(PlatformErrorCode.TENANT_NOT_FOUND, "Tenant not found"));
	}

	public Page<Tenant> list(Pageable pageable) {
		return tenantRepository.findAll(pageable);
	}

	private Tenant saveAndEvict(Tenant tenant) {
		Tenant saved = tenantRepository.save(tenant);
		tenantRegistry.evict(saved.getId());
		return saved;
	}

	private static Tenant requireMigrated(Tenant tenant) {
		if (tenant.getMigrationState() == SchemaMigrationState.FAILED) {
			throw new ApiException(PlatformErrorCode.TENANT_MIGRATION_FAILED, "Schema migration failed for tenant "
					+ tenant.getId() + "; fix the cause and retry POST /platform/tenants/" + tenant.getId() + "/migrate");
		}
		return tenant;
	}

	private static void requireStatus(Tenant tenant, TenantStatus expected, String action) {
		if (tenant.getStatus() != expected) {
			throw new ApiException(PlatformErrorCode.INVALID_TENANT_STATE,
					"Only " + expected + " tenants can be " + action + "; tenant is " + tenant.getStatus());
		}
	}

	private static ZoneId parseTimezone(String timezone) {
		try {
			return ZoneId.of(timezone);
		}
		catch (DateTimeException ex) {
			throw new ApiException(PlatformErrorCode.INVALID_TIMEZONE, "Unknown timezone: " + timezone);
		}
	}

}
