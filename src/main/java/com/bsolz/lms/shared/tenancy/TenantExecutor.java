package com.bsolz.lms.shared.tenancy;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Runs work as a tenant known only by id - e.g. an event listener binding the tenant carried in the
 * event, so it never depends on what happens to be bound on the executing thread (resubmitted
 * events run without one).
 */
@Component
@RequiredArgsConstructor
public class TenantExecutor {

	private final TenantRegistry tenantRegistry;

	public void run(UUID tenantId, Runnable task) {
		TenantInfo tenant = tenantRegistry.findById(tenantId)
				.orElseThrow(() -> new IllegalStateException("Unknown tenant " + tenantId));
		TenantContext.run(tenant, task);
	}

}
