/**
 * Tenant-level system settings (leave year start, timezone, date format, branding).
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Settings",
	allowedDependencies = {
		"shared"
	}
)
package com.bsolz.lms.settings;

import org.springframework.modulith.ApplicationModule;
