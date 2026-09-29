package com.bsolz.lms.settings.api;

import java.time.LocalDate;

/** Tenant settings for other modules. All calls need a tenant bound. */
public interface SettingsApi {

	SystemSettings current();

	/** Today's date in the tenant's timezone. */
	LocalDate today();

}
