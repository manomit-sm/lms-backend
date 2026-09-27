package com.bsolz.lms.shared.tenancy;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

/**
 * Hibernate schema-per-tenant routing: every connection handed to a session gets
 * {@code SET search_path TO "<tenant schema>"} before use and is reset to {@code public} on release.
 * The tenant identifier Hibernate passes in is the schema name itself (see
 * {@link TenantIdentifierResolver}), validated again by {@link TenantSchemas} before it reaches SQL.
 */
@Component
@RequiredArgsConstructor
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

	private final DataSource dataSource;

	@Override
	public Connection getAnyConnection() throws SQLException {
		return getConnection(TenantSchemas.PUBLIC_SCHEMA);
	}

	@Override
	public void releaseAnyConnection(Connection connection) throws SQLException {
		connection.close();
	}

	@Override
	public Connection getConnection(String schemaName) throws SQLException {
		Connection connection = TenantAwareDataSource.rawConnection(dataSource);
		try {
			TenantSchemas.applySearchPath(connection, schemaName);
			return connection;
		}
		catch (SQLException | RuntimeException ex) {
			connection.close();
			throw ex;
		}
	}

	@Override
	public void releaseConnection(String schemaName, Connection connection) throws SQLException {
		try {
			TenantSchemas.applySearchPath(connection, TenantSchemas.PUBLIC_SCHEMA);
		}
		finally {
			connection.close();
		}
	}

	@Override
	public boolean supportsAggressiveRelease() {
		return false;
	}

	@Override
	public boolean handlesConnectionSchema() {
		// search_path is set above; Hibernate must not also call Connection#setSchema itself.
		return true;
	}

	@Override
	public boolean isUnwrappableAs(Class<?> unwrapType) {
		return false;
	}

	@Override
	public <T> T unwrap(Class<T> unwrapType) {
		throw new UnsupportedOperationException("Cannot unwrap " + getClass().getName() + " as " + unwrapType.getName());
	}

}
