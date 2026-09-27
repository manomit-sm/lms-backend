package com.bsolz.lms.shared.tenancy;

/** Whether a tenant's schema is on the current tenant changelog. Independent of {@link TenantStatus}. */
public enum SchemaMigrationState {

	PENDING,
	UP_TO_DATE,
	FAILED

}
