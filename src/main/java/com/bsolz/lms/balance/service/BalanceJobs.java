package com.bsolz.lms.balance.service;

import com.bsolz.lms.settings.api.SettingsApi;
import com.bsolz.lms.shared.tenancy.TenantJobRunner;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules {@link BalanceMaintenanceService} for every tenant, once across all instances. It runs
 * several times a day so that each tenant's new day - in its own timezone - is picked up within hours.
 */
@Component
@RequiredArgsConstructor
class BalanceJobs {

	static final String BALANCE_MAINTENANCE = "balance-maintenance";

	private final TenantJobRunner jobRunner;

	private final BalanceMaintenanceService maintenance;

	private final SettingsApi settings;

	@Scheduled(cron = "${lms.jobs.balance-maintenance}", zone = "UTC")
	@SchedulerLock(name = BALANCE_MAINTENANCE, lockAtMostFor = "PT2H")
	void maintainBalances() {
		jobRunner.runForEachTenant(BALANCE_MAINTENANCE, tenant -> maintenance.run(settings.today()));
	}

}
