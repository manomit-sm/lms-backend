package com.bsolz.lms.shared.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/** Authentication for a tenant request once the tenant and user are resolved; replaces the bare JWT authentication. */
public class LmsAuthentication extends AbstractAuthenticationToken {

	private final LmsPrincipal principal;

	private final Jwt token;

	public LmsAuthentication(LmsPrincipal principal, Jwt token) {
		super(principal.authorities());
		this.principal = principal;
		this.token = token;
		setAuthenticated(true);
	}

	@Override
	public LmsPrincipal getPrincipal() {
		return principal;
	}

	@Override
	public Jwt getCredentials() {
		return token;
	}

	@Override
	public String getName() {
		return principal.subject();
	}

}
