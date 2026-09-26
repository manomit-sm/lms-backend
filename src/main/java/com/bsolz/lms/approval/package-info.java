/**
 * Approval workflow definitions and runtime instances/tasks, delegation and
 * escalation. Does not depend on {@code leave}; leave passes what it needs in.
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Approval",
	allowedDependencies = {
		"shared",
		"organization :: api",
		"identity :: api"
	}
)
package com.bsolz.lms.approval;

import org.springframework.modulith.ApplicationModule;
