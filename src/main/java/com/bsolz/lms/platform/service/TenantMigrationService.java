package com.bsolz.lms.platform.service;

import com.bsolz.lms.platform.entity.Tenant;
import com.bsolz.lms.platform.exception.PlatformErrorCode;
import com.bsolz.lms.platform.repository.TenantRepository;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.tenancy.SchemaMigrationState;
import com.bsolz.lms.shared.tenancy.TenantAwareDataSource;
import com.bsolz.lms.shared.tenancy.TenantSchemas;
import com.bsolz.lms.shared.tenancy.TenantStatus;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;

/**
 * Applies Liquibase changelogs: {@value #PUBLIC_CHANGELOG} to the public schema, then
 * {@value #TENANT_CHANGELOG} to every tenant schema (each schema keeps its own
 * {@code databasechangelog}). Replaces Spring Boot's single-schema Liquibase run, which is disabled.
 * <p>
 * Runs before the web server starts when {@code lms.platform.migrations.run-on-startup} is true
 * (local, tests, single-instance deployments). A tenant whose migration fails is marked
 * {@code FAILED} - it gets 503s, the other tenants carry on - and can be retried through
 * {@code POST /platform/tenants/{id}/migrate}. A public-schema failure aborts startup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantMigrationService implements SmartInitializingSingleton {

	static final String PUBLIC_CHANGELOG = "classpath:db/changelog/db.changelog-public.yaml";

	static final String TENANT_CHANGELOG = "classpath:db/changelog/db.changelog-master.yaml";

	private final DataSource dataSource;

	private final ResourceLoader resourceLoader;

	private final TenantRepository tenantRepository;

	private final TenantRegistryService tenantRegistry;

	private final Clock clock;

	@Value("${lms.platform.migrations.run-on-startup}")
	private boolean runOnStartup;

	@Override
	public void afterSingletonsInstantiated() {
		if (runOnStartup) {
			migratePublicSchema();
			migrateAllTenants();
		}
	}

	public void migratePublicSchema() {
		try {
			applyChangelog(TenantSchemas.PUBLIC_SCHEMA, PUBLIC_CHANGELOG);
			log.info("Public schema is up to date");
		}
		catch (Exception ex) {
			throw new IllegalStateException("Public schema migration failed", ex);
		}
	}

	public void migrateAllTenants() {
		var tenants = tenantRepository.findAllByStatusNot(TenantStatus.DEACTIVATED);
		log.info("Migrating {} tenant schema(s)", tenants.size());
		long failed = tenants.stream()
				.map(tenant -> migrateTenant(tenant.getId()))
				.filter(migrated -> migrated.getMigrationState() == SchemaMigrationState.FAILED)
				.count();
		if (failed > 0) {
			log.error("{} tenant schema migration(s) failed; those tenants are unavailable until retried", failed);
		}
	}

	/** Creates the tenant schema if needed and brings it up to date. Never throws for migration errors. */
	public Tenant migrateTenant(UUID tenantId) {
		Tenant tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ApiException(PlatformErrorCode.TENANT_NOT_FOUND, "Tenant not found"));
		try {
			try (Connection connection = rawConnection()) {
				TenantSchemas.createSchemaIfMissing(connection, tenant.getSchemaName());
			}
			applyChangelog(tenant.getSchemaName(), TENANT_CHANGELOG);
			tenant.markMigrated(Instant.now(clock));
			log.info("Tenant schema {} is up to date", tenant.getSchemaName());
		}
		catch (Exception ex) {
			log.error("Migration of tenant schema {} failed", tenant.getSchemaName(), ex);
			tenant.markMigrationFailed(ex.getMessage());
		}
		Tenant saved = tenantRepository.save(tenant);
		tenantRegistry.evict(tenantId);
		return saved;
	}

	private void applyChangelog(String schemaName, String changelog) throws Exception {
		try (Connection connection = rawConnection()) {
			TenantSchemas.applySearchPath(connection, schemaName);
			SpringLiquibase liquibase = new SpringLiquibase();
			liquibase.setDataSource(new SingleConnectionDataSource(connection, true));
			liquibase.setResourceLoader(resourceLoader);
			liquibase.setChangeLog(changelog);
			liquibase.setDefaultSchema(schemaName);
			liquibase.setLiquibaseSchema(schemaName);
			liquibase.afterPropertiesSet();
		}
	}

	private Connection rawConnection() throws SQLException {
		return TenantAwareDataSource.rawConnection(dataSource);
	}

}
