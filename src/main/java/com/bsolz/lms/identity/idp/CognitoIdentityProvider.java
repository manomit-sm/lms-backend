package com.bsolz.lms.identity.idp;

import com.bsolz.lms.identity.exception.IdentityErrorCode;
import com.bsolz.lms.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeliveryMediumType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

/**
 * Cognito admin API. Assumes the tenant user pool uses email as the username and defines the
 * immutable, app-client-read-only custom attribute {@value #TENANT_ATTRIBUTE}.
 */
@Slf4j
@RequiredArgsConstructor
public class CognitoIdentityProvider implements IdentityProviderClient, AutoCloseable {

	static final String TENANT_ATTRIBUTE = "custom:tenant_id";

	private final CognitoIdentityProviderClient cognito;

	private final String userPoolId;

	@Override
	public String inviteUser(String email, UUID tenantId) {
		try {
			AdminCreateUserResponse response = cognito.adminCreateUser(request -> request.userPoolId(userPoolId)
					.username(email)
					.userAttributes(attribute("email", email), attribute("email_verified", "true"),
							attribute(TENANT_ATTRIBUTE, tenantId.toString()))
					.desiredDeliveryMediums(DeliveryMediumType.EMAIL));
			log.info("Invited {} to Cognito user pool {}", email, userPoolId);
			return value(response.user().attributes(), "sub");
		}
		catch (UsernameExistsException ex) {
			AdminGetUserResponse existing = cognito
					.adminGetUser(request -> request.userPoolId(userPoolId).username(email));
			if (!tenantId.toString().equals(value(existing.userAttributes(), TENANT_ATTRIBUTE))) {
				throw new ApiException(IdentityErrorCode.EMAIL_REGISTERED_ELSEWHERE,
						"Email " + email + " is already registered with another organisation");
			}
			return value(existing.userAttributes(), "sub");
		}
	}

	@Override
	public void disableUser(String email) {
		cognito.adminDisableUser(request -> request.userPoolId(userPoolId).username(email));
		cognito.adminUserGlobalSignOut(request -> request.userPoolId(userPoolId).username(email));
	}

	@Override
	public void enableUser(String email) {
		cognito.adminEnableUser(request -> request.userPoolId(userPoolId).username(email));
	}

	@Override
	public void close() {
		cognito.close();
	}

	private static AttributeType attribute(String name, String value) {
		return AttributeType.builder().name(name).value(value).build();
	}

	private static String value(List<AttributeType> attributes, String name) {
		return attributes.stream()
				.filter(attribute -> attribute.name().equals(name))
				.map(AttributeType::value)
				.findFirst()
				.orElse(null);
	}

}
