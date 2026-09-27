package com.bsolz.lms.shared.security.local;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Opens {@code /local/tokens/**} - only when the local issuer is enabled. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("lms.security.local-issuer.enabled")
class LocalSecurityConfig {

	@Bean
	@Order(0)
	SecurityFilterChain localTokenSecurityFilterChain(HttpSecurity http) throws Exception {
		return http.securityMatcher("/local/tokens/**")
				.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.build();
	}

}
