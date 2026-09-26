/**
 * Tenant registry (public schema), tenant provisioning and lifecycle, per-tenant
 * Liquibase migrations, and the platform admin API ({@code /platform/**}).
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Platform",
	allowedDependencies = {
		"shared",
		"identity :: api"
	}
)
package com.bsolz.lms.platform;

import org.springframework.modulith.ApplicationModule;
