package com.bsolz.lms.shared.security;

import com.bsolz.lms.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum SecurityErrorCode implements ErrorCode {

	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
	ACCESS_DENIED(HttpStatus.FORBIDDEN),
	/** Unknown or deactivated tenant (deliberately indistinguishable). */
	TENANT_NOT_FOUND(HttpStatus.FORBIDDEN),
	TENANT_SUSPENDED(HttpStatus.FORBIDDEN),
	/** Tenant exists but is still provisioning or its schema migration failed. */
	TENANT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
	/** Valid token, but the subject has no active user in this tenant. */
	USER_NOT_REGISTERED(HttpStatus.FORBIDDEN);

	private final HttpStatus status;

}
