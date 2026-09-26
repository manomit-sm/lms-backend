/**
 * Leave balances and the append-only balance ledger; allocation, accrual,
 * carry-forward and expiry jobs.
 * <p>
 * Other modules may only use types from the {@code api} package.
 */
@ApplicationModule(
	displayName = "Balance",
	allowedDependencies = {
		"shared",
		"organization :: api",
		"leavepolicy :: api",
		"settings :: api"
	}
)
package com.bsolz.lms.balance;

import org.springframework.modulith.ApplicationModule;
