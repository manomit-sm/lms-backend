package com.bsolz.lms.shared.security;

import com.bsolz.lms.shared.exception.ProblemDetailResponseWriter;
import com.bsolz.lms.shared.security.local.LocalTokenIssuer;
import com.bsolz.lms.shared.tenancy.TenantRegistry;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Three stateless filter chains:
 * <ol>
 * <li>{@code /platform/**} - platform-admin pool tokens, {@code platform-admins} group required;</li>
 * <li>{@code /api/**} - tenant pool tokens, tenant resolved and bound by {@link TenantContextFilter};</li>
 * <li>everything else - health, info and API docs are public; all other paths are denied.</li>
 * </ol>
 * Signing keys come from the issuer's OIDC discovery (Cognito), or from {@link LocalTokenIssuer}
 * when {@code lms.security.local-issuer.enabled} is set for local development and tests.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {

	private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

	@Bean
	@Order(1)
	SecurityFilterChain platformSecurityFilterChain(HttpSecurity http, SecurityProperties properties,
			ObjectProvider<LocalTokenIssuer> localIssuer, ProblemDetailSecurityHandlers handlers) throws Exception {
		JwtDecoder decoder = jwtDecoder(properties.platform(), LmsJwtValidators.forPlatform(properties.platform()),
				localIssuer);
		http.securityMatcher("/platform/**")
				.authorizeHttpRequests(authorize -> authorize.anyRequest().hasRole(PLATFORM_ADMIN_ROLE))
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(SecurityConfig::platformAuthentication))
						.authenticationEntryPoint(handlers.authenticationEntryPoint())
						.accessDeniedHandler(handlers.accessDeniedHandler()));
		return statelessApi(http, handlers).build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain tenantSecurityFilterChain(HttpSecurity http, SecurityProperties properties,
			ObjectProvider<LocalTokenIssuer> localIssuer, ProblemDetailSecurityHandlers handlers,
			TenantRegistry tenantRegistry, ObjectProvider<CurrentUserLoader> currentUserLoader,
			ProblemDetailResponseWriter problemWriter) throws Exception {
		JwtDecoder decoder = jwtDecoder(properties.tenant(), LmsJwtValidators.forTenant(properties.tenant()),
				localIssuer);
		http.securityMatcher("/api/**")
				.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(jwt -> jwt.decoder(decoder)
								.jwtAuthenticationConverter(token -> new JwtAuthenticationToken(token, List.of(), token.getSubject())))
						.authenticationEntryPoint(handlers.authenticationEntryPoint())
						.accessDeniedHandler(handlers.accessDeniedHandler()))
				.addFilterAfter(new TenantContextFilter(tenantRegistry, currentUserLoader, problemWriter),
						BearerTokenAuthenticationFilter.class);
		return statelessApi(http, handlers).build();
	}

	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http, ProblemDetailSecurityHandlers handlers)
			throws Exception {
		http.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**",
						"/swagger-ui.html", "/error")
				.permitAll()
				.anyRequest().denyAll());
		return statelessApi(http, handlers).build();
	}

	private static HttpSecurity statelessApi(HttpSecurity http, ProblemDetailSecurityHandlers handlers)
			throws Exception {
		return http.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(handlers.authenticationEntryPoint())
						.accessDeniedHandler(handlers.accessDeniedHandler()));
	}

	private static JwtDecoder jwtDecoder(SecurityProperties.Realm realm, OAuth2TokenValidator<Jwt> validator,
			ObjectProvider<LocalTokenIssuer> localIssuer) {
		LocalTokenIssuer local = localIssuer.getIfAvailable();
		if (local != null) {
			NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(local.publicKey()).build();
			decoder.setJwtValidator(validator);
			return decoder;
		}
		// Lazy: OIDC discovery happens on the first request, so startup doesn't depend on the issuer being reachable.
		return new SupplierJwtDecoder(() -> {
			NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(realm.issuerUri().toString());
			decoder.setJwtValidator(validator);
			return decoder;
		});
	}

	private static JwtAuthenticationToken platformAuthentication(Jwt jwt) {
		List<String> groups = jwt.getClaimAsStringList(LmsClaims.GROUPS);
		List<GrantedAuthority> authorities = groups != null && groups.contains(LmsClaims.PLATFORM_ADMIN_GROUP)
				? List.of(new SimpleGrantedAuthority("ROLE_" + PLATFORM_ADMIN_ROLE))
				: List.of();
		return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
	}

}
