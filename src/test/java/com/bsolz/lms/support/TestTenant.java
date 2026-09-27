package com.bsolz.lms.support;

import com.bsolz.lms.shared.tenancy.TenantInfo;
import java.util.UUID;

/** A provisioned tenant and its first tenant admin. */
public record TestTenant(TenantInfo info, String adminEmail, String adminSubject) {

	public UUID id() {
		return info.id();
	}

}
