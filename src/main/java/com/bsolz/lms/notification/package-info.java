/**
 * In-app notifications (SSE) driven by domain events; email later. Depends on {@code leavepolicy} for
 * leave type names in notification texts and on {@code settings} for the tenant's timezone.
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Notification",
	allowedDependencies = {
		"shared",
		"identity :: api",
		"organization :: api",
		"leavepolicy :: api",
		"settings :: api",
		"leave :: api",
		"approval :: api",
		"balance :: api"
	}
)
package com.bsolz.lms.notification;

import org.springframework.modulith.ApplicationModule;
