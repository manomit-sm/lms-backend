package com.bsolz.lms.shared.tenancy;

/** Tenant lifecycle: {@code PROVISIONING -> ACTIVE <-> SUSPENDED -> DEACTIVATED}. */
public enum TenantStatus {

	PROVISIONING,
	ACTIVE,
	SUSPENDED,
	DEACTIVATED

}
