package com.bsolz.lms.identity.exception;

import com.bsolz.lms.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum IdentityErrorCode implements ErrorCode {

	USER_NOT_FOUND(HttpStatus.NOT_FOUND),
	ROLE_NOT_FOUND(HttpStatus.NOT_FOUND),
	UNKNOWN_ROLE(HttpStatus.BAD_REQUEST),
	UNKNOWN_PERMISSION(HttpStatus.BAD_REQUEST),
	USER_ALREADY_EXISTS(HttpStatus.CONFLICT),
	/** The identity provider already has this email under another tenant (one tenant per person). */
	EMAIL_REGISTERED_ELSEWHERE(HttpStatus.CONFLICT),
	EMAIL_LINKED_TO_OTHER_EMPLOYEE(HttpStatus.CONFLICT),
	ROLE_CODE_TAKEN(HttpStatus.CONFLICT),
	SYSTEM_ROLE_READ_ONLY(HttpStatus.CONFLICT),
	ROLE_IN_USE(HttpStatus.CONFLICT),
	LAST_TENANT_ADMIN(HttpStatus.CONFLICT),
	CANNOT_DISABLE_SELF(HttpStatus.CONFLICT),
	/** Granting permissions the acting user doesn't hold. */
	PRIVILEGE_ESCALATION(HttpStatus.FORBIDDEN);

	private final HttpStatus status;

}
