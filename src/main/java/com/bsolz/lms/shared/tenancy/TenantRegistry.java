package com.bsolz.lms.shared.tenancy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Looks up tenants in the control-plane registry. Declared here (shared kernel) so tenancy and
 * security infrastructure can use it; implemented by the {@code platform} module, which owns the
 * {@code public.tenant} table.
 */
public interface TenantRegistry {

	Optional<TenantInfo> findById(UUID tenantId);

	/** Tenants that are {@link TenantInfo#isServing() serving}, for scheduled jobs. */
	List<TenantInfo> findAllServing();

}
