/**
 * Read-only calendar views combining leaves, holidays and team availability.
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Calendar",
	allowedDependencies = {
		"shared",
		"organization :: api",
		"leavepolicy :: api",
		"leave :: api",
		"holiday :: api"
	}
)
package com.bsolz.lms.calendar;

import org.springframework.modulith.ApplicationModule;
