package com.bsolz.lms.platform.entity;

import com.bsolz.lms.shared.entity.BaseEntity;
import com.bsolz.lms.shared.tenancy.SchemaMigrationState;
import com.bsolz.lms.shared.tenancy.TenantInfo;
import com.bsolz.lms.shared.tenancy.TenantSchemas;
import com.bsolz.lms.shared.tenancy.TenantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A customer organisation. Lives in the public schema (explicitly qualified so it maps correctly
 * whichever tenant is bound); the tenant's own data lives in {@link #getSchemaName()}.
 */
@Getter
@Entity
@Table(name = "tenant", schema = TenantSchemas.PUBLIC_SCHEMA)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant extends BaseEntity {

	private static final int MAX_MIGRATION_ERROR_LENGTH = 2000;

	@Column(updatable = false)
	private String tenantKey;

	private String name;

	@Column(updatable = false)
	private String schemaName;

	private String subdomain;

	@Enumerated(EnumType.STRING)
	private TenantStatus status;

	@Enumerated(EnumType.STRING)
	private SchemaMigrationState migrationState;

	private Instant migratedAt;

	private String migrationError;

	private String defaultTimezone;

	public static Tenant provision(String tenantKey, String name, String subdomain, ZoneId defaultTimezone) {
		Tenant tenant = new Tenant();
		tenant.tenantKey = tenantKey;
		tenant.name = name;
		tenant.schemaName = TenantSchemas.schemaFor(tenantKey);
		tenant.subdomain = subdomain;
		tenant.status = TenantStatus.PROVISIONING;
		tenant.migrationState = SchemaMigrationState.PENDING;
		tenant.defaultTimezone = defaultTimezone.getId();
		return tenant;
	}

	/** Schema is on the current changelog; a tenant still provisioning becomes active. */
	public void markMigrated(Instant at) {
		migrationState = SchemaMigrationState.UP_TO_DATE;
		migratedAt = at;
		migrationError = null;
		if (status == TenantStatus.PROVISIONING) {
			status = TenantStatus.ACTIVE;
		}
	}

	public void markMigrationFailed(String error) {
		migrationState = SchemaMigrationState.FAILED;
		migrationError = error == null || error.length() <= MAX_MIGRATION_ERROR_LENGTH
				? error
				: error.substring(0, MAX_MIGRATION_ERROR_LENGTH);
	}

	public void suspend() {
		status = TenantStatus.SUSPENDED;
	}

	public void activate() {
		status = TenantStatus.ACTIVE;
	}

	public TenantInfo toInfo() {
		return new TenantInfo(getId(), tenantKey, schemaName, status, migrationState, ZoneId.of(defaultTimezone));
	}

}
