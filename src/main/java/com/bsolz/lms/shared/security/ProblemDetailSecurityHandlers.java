package com.bsolz.lms.shared.security;

import com.bsolz.lms.shared.exception.ProblemDetailResponseWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 401/403 responses from the security filter chains as problem+json with an {@code errorCode}.
 * The bearer-token handlers still run first so the {@code WWW-Authenticate} header is set per RFC 6750.
 */
@Component
@RequiredArgsConstructor
class ProblemDetailSecurityHandlers {

	private final ProblemDetailResponseWriter problemWriter;

	AuthenticationEntryPoint authenticationEntryPoint() {
		BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
		return (request, response, ex) -> {
			bearer.commence(request, response, ex);
			problemWriter.write(request, response, SecurityErrorCode.UNAUTHENTICATED,
					"A valid access token is required");
		};
	}

	AccessDeniedHandler accessDeniedHandler() {
		BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
		return (request, response, ex) -> {
			bearer.handle(request, response, ex);
			problemWriter.write(request, response, SecurityErrorCode.ACCESS_DENIED,
					"You do not have permission to perform this action");
		};
	}

}
