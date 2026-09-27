package com.bsolz.lms.shared.tenancy;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Immutable snapshot of a tenant, as bound to the current execution by {@link TenantContext}.
 *
 * @param id the tenant id carried in the access token's {@code tenant_id} claim
 * @param key the human-readable tenant key (e.g. {@code acme}) the schema name is derived from
 * @param schemaName the tenant's Postgres schema (e.g. {@code tenant_acme})
 */
public record TenantInfo(UUID id, String key, String schemaName, TenantStatus status,
		SchemaMigrationState migrationState, ZoneId timezone) {

	/** A tenant serves requests and jobs only when active and its schema is fully migrated. */
	public boolean isServing() {
		return status == TenantStatus.ACTIVE && migrationState == SchemaMigrationState.UP_TO_DATE;
	}

}
