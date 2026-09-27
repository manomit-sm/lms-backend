package com.bsolz.lms.shared.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

/** Fills {@code created_by}/{@code updated_by} with the current tenant user's id, when there is one. */
@Component("securityAuditorAware")
class SecurityAuditorAware implements AuditorAware<UUID> {

	@Override
	public Optional<UUID> getCurrentAuditor() {
		return CurrentUser.find().map(LmsPrincipal::userId);
	}

}
