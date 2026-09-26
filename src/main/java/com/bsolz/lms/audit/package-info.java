/**
 * Activity log and recent-activity feed, populated from other modules' domain events.
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Audit",
	allowedDependencies = {
		"shared",
		"identity :: api",
		"organization :: api",
		"settings :: api",
		"leavepolicy :: api",
		"holiday :: api",
		"balance :: api",
		"approval :: api",
		"leave :: api"
	}
)
package com.bsolz.lms.audit;

import org.springframework.modulith.ApplicationModule;
