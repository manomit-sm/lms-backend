package com.bsolz.lms.identity.idp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bsolz.lms.shared.exception.ApiException;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

class CognitoIdentityProviderTest {

	private static final String POOL = "eu-west-1_pool";

	private static final UUID TENANT = UUID.randomUUID();

	private final CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);

	private final CognitoIdentityProvider provider = new CognitoIdentityProvider(cognito, POOL);

	@Test
	@SuppressWarnings("unchecked")
	void invitesWithTenantAttributeAndReturnsSubject() {
		when(cognito.adminCreateUser(any(Consumer.class))).thenReturn(AdminCreateUserResponse.builder()
				.user(UserType.builder().attributes(attribute("sub", "sub-123")).build())
				.build());

		assertThat(provider.inviteUser("ann@example.test", TENANT)).isEqualTo("sub-123");

		ArgumentCaptor<Consumer<AdminCreateUserRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
		verify(cognito).adminCreateUser(captor.capture());
		AdminCreateUserRequest.Builder builder = AdminCreateUserRequest.builder();
		captor.getValue().accept(builder);
		AdminCreateUserRequest request = builder.build();
		assertThat(request.userPoolId()).isEqualTo(POOL);
		assertThat(request.username()).isEqualTo("ann@example.test");
		assertThat(request.userAttributes()).contains(attribute("custom:tenant_id", TENANT.toString()),
				attribute("email_verified", "true"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void reusesExistingUserOfTheSameTenant() {
		when(cognito.adminCreateUser(any(Consumer.class))).thenThrow(UsernameExistsException.builder().build());
		when(cognito.adminGetUser(any(Consumer.class))).thenReturn(existing(TENANT.toString()));

		assertThat(provider.inviteUser("ann@example.test", TENANT)).isEqualTo("sub-existing");
	}

	@Test
	@SuppressWarnings("unchecked")
	void refusesAnEmailOwnedByAnotherTenant() {
		when(cognito.adminCreateUser(any(Consumer.class))).thenThrow(UsernameExistsException.builder().build());
		when(cognito.adminGetUser(any(Consumer.class))).thenReturn(existing(UUID.randomUUID().toString()));

		assertThatThrownBy(() -> provider.inviteUser("ann@example.test", TENANT)).isInstanceOf(ApiException.class)
				.hasMessageContaining("another organisation");
	}

	@Test
	@SuppressWarnings("unchecked")
	void disablingAlsoSignsOutEverywhere() {
		provider.disableUser("ann@example.test");

		verify(cognito).adminDisableUser(any(Consumer.class));
		verify(cognito).adminUserGlobalSignOut(any(Consumer.class));
	}

	private static AdminGetUserResponse existing(String tenantId) {
		return AdminGetUserResponse.builder()
				.userAttributes(attribute("sub", "sub-existing"), attribute("custom:tenant_id", tenantId))
				.build();
	}

	private static AttributeType attribute(String name, String value) {
		return AttributeType.builder().name(name).value(value).build();
	}

}
