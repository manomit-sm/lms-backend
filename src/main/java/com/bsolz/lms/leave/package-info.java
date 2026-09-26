/**
 * Leave requests, per-day breakdown, attachments, status history and the
 * leave status machine.
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Leave",
	allowedDependencies = {
		"shared",
		"organization :: api",
		"leavepolicy :: api",
		"balance :: api",
		"holiday :: api",
		"approval :: api",
		"settings :: api"
	}
)
package com.bsolz.lms.leave;

import org.springframework.modulith.ApplicationModule;
