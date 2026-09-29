package com.bsolz.lms.shared.tenancy;

import java.util.UUID;

/** A test-only domain event for {@link FlakyProbeListener}. */
public record ProbeEvent(UUID tenantId, UUID probeId) {
}
