package com.bsolz.lms.shared.tenancy;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Wraps the application's connection pool so that <em>every</em> borrowed connection has its
 * {@code search_path} set to the current tenant's schema (or {@code public} when no tenant is
 * bound). This covers everything that doesn't go through Hibernate's multi-tenant connection
 * provider - {@code JdbcTemplate}, native report queries, ShedLock - so no code path can run
 * against a stale schema left on a pooled connection.
 * <p>
 * Inside a JPA transaction, {@code JdbcTemplate} reuses the transaction's connection (already
 * switched by {@link SchemaMultiTenantConnectionProvider}); this wrapper only acts on a fresh borrow.
 * Installed around the auto-configured pool by {@link MultiTenancyConfig}.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

	public TenantAwareDataSource(DataSource targetDataSource) {
		super(targetDataSource);
	}

	@Override
	public Connection getConnection() throws SQLException {
		return switchToCurrentSchema(obtainTargetDataSource().getConnection());
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return switchToCurrentSchema(obtainTargetDataSource().getConnection(username, password));
	}

	/** A connection straight from the pool, for callers that set the schema themselves. */
	public Connection getRawConnection() throws SQLException {
		return obtainTargetDataSource().getConnection();
	}

	/** A pool connection with no schema applied, whether or not {@code dataSource} is wrapped. */
	public static Connection rawConnection(DataSource dataSource) throws SQLException {
		return dataSource instanceof TenantAwareDataSource tenantAware
				? tenantAware.getRawConnection()
				: dataSource.getConnection();
	}

	private static Connection switchToCurrentSchema(Connection connection) throws SQLException {
		try {
			TenantSchemas.applySearchPath(connection, TenantSchemas.currentSchema());
			return connection;
		}
		catch (SQLException | RuntimeException ex) {
			connection.close();
			throw ex;
		}
	}

}
