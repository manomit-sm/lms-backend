package com.bsolz.lms.shared.tenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which tenant a new session belongs to: the bound tenant's schema name, or
 * {@code public} when no tenant is bound (platform/control-plane work, application startup).
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

	@Override
	public String resolveCurrentTenantIdentifier() {
		return TenantSchemas.currentSchema();
	}

	@Override
	public boolean validateExistingCurrentSessions() {
		return true;
	}

}
