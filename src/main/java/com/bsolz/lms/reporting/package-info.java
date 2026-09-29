/**
 * Dashboard statistics, reports and asynchronous report exports. Reports are read models: they query
 * other modules' tables directly with read-only SQL (joins across modules are what reports are), and
 * never write to them. Depends on {@code settings} for the tenant's "today" and timezone.
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Reporting",
	allowedDependencies = {
		"shared",
		"settings :: api",
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
