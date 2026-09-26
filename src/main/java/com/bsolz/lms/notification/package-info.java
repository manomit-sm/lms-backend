/**
 * In-app notifications (SSE) driven by domain events; email later.
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Notification",
	allowedDependencies = {
		"shared",
		"identity :: api",
		"organization :: api",
		"leave :: api",
		"approval :: api",
		"balance :: api"
	}
)
package com.bsolz.lms.notification;

import org.springframework.modulith.ApplicationModule;
