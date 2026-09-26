/**
 * Application users mapped to Cognito subjects, roles, permissions, the
 * Cognito admin client for user lifecycle, and {@code /auth/me}.
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Identity",
	allowedDependencies = {
		"shared",
		"organization :: api"
	}
)
package com.bsolz.lms.identity;

import org.springframework.modulith.ApplicationModule;
