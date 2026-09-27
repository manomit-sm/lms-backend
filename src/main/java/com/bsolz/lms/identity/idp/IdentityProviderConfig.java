package com.bsolz.lms.identity.idp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityProviderConfig {

	@Bean
	@ConditionalOnProperty(name = "lms.identity.provider", havingValue = "cognito")
	CognitoIdentityProvider cognitoIdentityProvider(IdentityProperties properties) {
		IdentityProperties.Cognito cognito = properties.cognito();
		if (cognito == null || !StringUtils.hasText(cognito.userPoolId()) || !StringUtils.hasText(cognito.region())) {
			throw new IllegalStateException("lms.identity.cognito.user-pool-id and region are required");
		}
		CognitoIdentityProviderClient client = CognitoIdentityProviderClient.builder()
				.region(Region.of(cognito.region()))
				.build();
		return new CognitoIdentityProvider(client, cognito.userPoolId());
	}

	@Bean
	@ConditionalOnProperty(name = "lms.identity.provider", havingValue = "fake")
	FakeIdentityProvider fakeIdentityProvider(Environment environment) {
		if (environment.acceptsProfiles(Profiles.of("prod", "aws"))) {
			throw new IllegalStateException("The fake identity provider must never be enabled in a deployed environment");
		}
		return new FakeIdentityProvider();
	}

}
