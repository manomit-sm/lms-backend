/**
 * Approval workflow definitions and runtime instances/tasks, delegation and
 * escalation. Does not depend on {@code leave}: leave starts an approval with what the workflow needs
 * and reacts to its events. Depends on {@code leavepolicy} only to validate leave types in workflow rules.
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Approval",
	allowedDependencies = {
		"shared",
		"organization :: api",
		"identity :: api",
		"leavepolicy :: api"
	}
)
package com.bsolz.lms.approval;

import org.springframework.modulith.ApplicationModule;
