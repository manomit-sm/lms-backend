package com.bsolz.lms.shared.tenancy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Test-only listener whose first delivery of each {@link ProbeEvent} fails, so that the event stays
 * incomplete in the registry until it is resubmitted. Records which tenant each delivery ran as.
 */
@Component
public class FlakyProbeListener {

	private final Map<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();

	private final Map<UUID, String> tenantKeys = new ConcurrentHashMap<>();

	@Async
	@TransactionalEventListener
	void on(ProbeEvent event) {
		int attempt = attempts.computeIfAbsent(event.probeId(), id -> new AtomicInteger()).incrementAndGet();
		TenantContext.current().ifPresent(tenant -> tenantKeys.put(event.probeId(), tenant.key()));
		if (attempt == 1) {
			throw new IllegalStateException("First delivery of probe " + event.probeId() + " fails on purpose");
		}
	}

	public int attempts(UUID probeId) {
		return attempts.getOrDefault(probeId, new AtomicInteger()).get();
	}

	public String tenantKeyOfLastDelivery(UUID probeId) {
		return tenantKeys.get(probeId);
	}

}
