package com.bsolz.lms.shared.tenancy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * Tenant schema naming and {@code search_path} switching - the single place where a schema name
 * is put into SQL. Schema names are validated against a strict pattern before use, so the quoted
 * identifier below can never carry injected SQL.
 */
public final class TenantSchemas {

	public static final String PUBLIC_SCHEMA = "public";

	public static final String SCHEMA_PREFIX = "tenant_";

	/** 3-40 chars: lowercase letters, digits and hyphens; starts with a letter, doesn't end with a hyphen. */
	public static final String TENANT_KEY_REGEX = "^[a-z][a-z0-9-]{1,38}[a-z0-9]$";

	private static final Pattern TENANT_KEY = Pattern.compile(TENANT_KEY_REGEX);

	private static final Pattern SCHEMA_NAME = Pattern.compile("^(public|tenant_[a-z][a-z0-9_]{1,38}[a-z0-9])$");

	private TenantSchemas() {
	}

	/** {@code acme-corp} becomes {@code tenant_acme_corp}. */
	public static String schemaFor(String tenantKey) {
		if (tenantKey == null || !TENANT_KEY.matcher(tenantKey).matches()) {
			throw new IllegalArgumentException("Invalid tenant key: " + tenantKey);
		}
		return SCHEMA_PREFIX + tenantKey.replace('-', '_');
	}

	public static String requireValidSchema(String schemaName) {
		if (schemaName == null || !SCHEMA_NAME.matcher(schemaName).matches()) {
			throw new IllegalArgumentException("Invalid schema name: " + schemaName);
		}
		return schemaName;
	}

	/** The schema for the tenant bound to the current execution, or {@code public} when none is bound. */
	public static String currentSchema() {
		return TenantContext.current().map(TenantInfo::schemaName).orElse(PUBLIC_SCHEMA);
	}

	public static void applySearchPath(Connection connection, String schemaName) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("SET search_path TO \"" + requireValidSchema(schemaName) + "\"");
		}
	}

	public static void createSchemaIfMissing(Connection connection, String schemaName) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA IF NOT EXISTS \"" + requireValidSchema(schemaName) + "\"");
		}
	}

}
