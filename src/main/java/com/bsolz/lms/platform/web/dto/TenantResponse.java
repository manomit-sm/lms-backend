package com.bsolz.lms.platform.web.dto;

import com.bsolz.lms.shared.tenancy.SchemaMigrationState;
import com.bsolz.lms.shared.tenancy.TenantStatus;
import java.time.Instant;
import java.util.UUID;

public record TenantResponse(UUID id, String key, String name, String schemaName, String subdomain,
		TenantStatus status, SchemaMigrationState migrationState, Instant migratedAt, String migrationError,
		String defaultTimezone, Instant createdAt, Instant updatedAt) {
}
