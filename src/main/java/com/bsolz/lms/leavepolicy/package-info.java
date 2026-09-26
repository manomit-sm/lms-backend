/**
 * Leave types, leave policies and their applicability rules, and leave periods.
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Leave Policy",
	allowedDependencies = {
		"shared",
		"organization :: api",
		"settings :: api"
	}
)
package com.bsolz.lms.leavepolicy;

import org.springframework.modulith.ApplicationModule;
