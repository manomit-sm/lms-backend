/**
 * Departments, designations, locations, work schedules and employees,
 * including the reporting-manager hierarchy.
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Organization",
	allowedDependencies = {
		"shared"
	}
)
package com.bsolz.lms.organization;

import org.springframework.modulith.ApplicationModule;
