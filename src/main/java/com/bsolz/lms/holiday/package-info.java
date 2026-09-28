/**
 * Company/public holidays and their department/location applicability.
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Holiday",
	allowedDependencies = {
		"shared",
		"organization :: api",
		"settings :: api"
	}
)
package com.bsolz.lms.holiday;

import org.springframework.modulith.ApplicationModule;
