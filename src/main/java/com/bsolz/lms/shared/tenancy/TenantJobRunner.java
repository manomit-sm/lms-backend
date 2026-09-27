package com.bsolz.lms.shared.tenancy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Runs a scheduled job once per serving tenant, with that tenant bound. A failure for one tenant is
 * logged and reported but never stops the job for the others.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantJobRunner {

	private final TenantRegistry tenantRegistry;

	public JobRunResult runForEachTenant(String jobName, Consumer<TenantInfo> task) {
		int succeeded = 0;
		List<UUID> failedTenantIds = new ArrayList<>();
		for (TenantInfo tenant : tenantRegistry.findAllServing()) {
			try {
				TenantContext.run(tenant, () -> task.accept(tenant));
				succeeded++;
			}
			catch (RuntimeException ex) {
				failedTenantIds.add(tenant.id());
				log.error("Job {} failed for tenant {}", jobName, tenant.key(), ex);
			}
		}
		log.info("Job {} finished: {} tenant(s) succeeded, {} failed", jobName, succeeded, failedTenantIds.size());
		return new JobRunResult(jobName, succeeded, List.copyOf(failedTenantIds));
	}

	public record JobRunResult(String jobName, int succeeded, List<UUID> failedTenantIds) {
	}

}
