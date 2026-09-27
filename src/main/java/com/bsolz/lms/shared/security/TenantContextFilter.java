package com.bsolz.lms.shared.security;

import com.bsolz.lms.shared.exception.ProblemDetailResponseWriter;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.shared.tenancy.TenantInfo;
import com.bsolz.lms.shared.tenancy.TenantRegistry;
import com.bsolz.lms.shared.tenancy.TenantStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the tenant for an authenticated {@code /api/**} request and binds it for the rest of
 * the request. Runs after bearer-token authentication, which has already verified the token and
 * that it carries a UUID {@code tenant_id} (see {@link LmsJwtValidators}).
 * <ol>
 * <li>{@code tenant_id} is looked up in the tenant registry - the only source of the tenant.</li>
 * <li>Unknown/deactivated → 403, suspended → 403, provisioning or failed migration → 503.</li>
 * <li>With the tenant bound, the subject's user and permissions are loaded from the tenant schema
 * and the authentication is replaced by an {@link LmsAuthentication}.</li>
 * </ol>
 * Not a Spring bean on purpose: it must only run inside the tenant security filter chain.
 */
@RequiredArgsConstructor
class TenantContextFilter extends OncePerRequestFilter {

	private final TenantRegistry tenantRegistry;

	private final ObjectProvider<CurrentUserLoader> currentUserLoader;

	private final ProblemDetailResponseWriter problemWriter;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
			chain.doFilter(request, response);
			return;
		}
		Jwt jwt = jwtAuthentication.getToken();
		UUID tenantId = UUID.fromString(jwt.getClaimAsString(LmsClaims.TENANT_ID));
		Optional<TenantInfo> tenant = tenantRegistry.findById(tenantId);
		if (tenant.isEmpty() || tenant.get().status() == TenantStatus.DEACTIVATED) {
			problemWriter.write(request, response, SecurityErrorCode.TENANT_NOT_FOUND, "Tenant not found");
			return;
		}
		if (tenant.get().status() == TenantStatus.SUSPENDED) {
			problemWriter.write(request, response, SecurityErrorCode.TENANT_SUSPENDED, "Tenant is suspended");
			return;
		}
		if (!tenant.get().isServing()) {
			problemWriter.write(request, response, SecurityErrorCode.TENANT_UNAVAILABLE,
					"Tenant is temporarily unavailable");
			return;
		}
		try {
			TenantContext.call(tenant.get(), () -> {
				filterAsTenant(tenant.get(), jwt, request, response, chain);
				return null;
			});
		}
		catch (IOException | ServletException | RuntimeException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new ServletException(ex);
		}
	}

	private void filterAsTenant(TenantInfo tenant, Jwt jwt, HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		LmsPrincipal principal;
		CurrentUserLoader loader = currentUserLoader.getIfAvailable();
		if (loader == null) {
			principal = new LmsPrincipal(jwt.getSubject(), tenant, null, null, Set.of(), Set.of());
		}
		else {
			Optional<UserAccess> access = loader.loadBySubject(jwt.getSubject());
			if (access.isEmpty() || !access.get().active()) {
				problemWriter.write(request, response, SecurityErrorCode.USER_NOT_REGISTERED,
						"User is not registered in this tenant");
				return;
			}
			UserAccess user = access.get();
			principal = new LmsPrincipal(jwt.getSubject(), tenant, user.userId(), user.employeeId(), user.roles(),
					user.permissions());
		}
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new LmsAuthentication(principal, jwt));
		SecurityContextHolder.setContext(context);
		chain.doFilter(request, response);
	}

}
