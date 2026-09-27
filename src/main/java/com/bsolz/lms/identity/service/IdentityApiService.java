package com.bsolz.lms.identity.service;

import com.bsolz.lms.identity.api.IdentityApi;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class IdentityApiService implements IdentityApi {

	private final UserAccountService accountService;

	@Override
	public UUID ensureTenantAdmin(String email) {
		return accountService.ensureTenantAdmin(email).getId();
	}

}
