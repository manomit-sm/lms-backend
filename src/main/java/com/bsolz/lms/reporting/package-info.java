/**
 * Dashboard statistics, reports and asynchronous report exports.
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Reporting",
	allowedDependencies = {
		"shared",
		"organization :: api",
		"leavepolicy :: api",
		"holiday :: api",
		"balance :: api",
		"leave :: api",
		"approval :: api"
	}
)
package com.bsolz.lms.reporting;

import org.springframework.modulith.ApplicationModule;
