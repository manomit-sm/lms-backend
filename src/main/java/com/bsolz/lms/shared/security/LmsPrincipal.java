package com.bsolz.lms.shared.security;

import com.bsolz.lms.shared.tenancy.TenantInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated tenant user for the current request.
 *
 * @param subject the identity provider's subject ({@code sub})
 * @param userId the tenant user's id, or null if not yet resolved
 * @param roles role codes, exposed as {@code ROLE_<code>} authorities
 * @param permissions permission codes, exposed as authorities as-is
 */
public record LmsPrincipal(String subject, TenantInfo tenant, UUID userId, UUID employeeId, Set<String> roles,
		Set<String> permissions) {

	public List<GrantedAuthority> authorities() {
		List<GrantedAuthority> authorities = new ArrayList<>();
		roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
		permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
		return authorities;
	}

}
