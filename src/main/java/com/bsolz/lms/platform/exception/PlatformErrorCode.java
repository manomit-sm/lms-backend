package com.bsolz.lms.platform.exception;

import com.bsolz.lms.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PlatformErrorCode implements ErrorCode {

	TENANT_NOT_FOUND(HttpStatus.NOT_FOUND),
	TENANT_KEY_TAKEN(HttpStatus.CONFLICT),
	SUBDOMAIN_TAKEN(HttpStatus.CONFLICT),
	INVALID_TENANT_STATE(HttpStatus.CONFLICT),
	INVALID_TIMEZONE(HttpStatus.BAD_REQUEST),
	TENANT_NOT_SERVING(HttpStatus.CONFLICT),
	TENANT_MIGRATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
	TENANT_ADMIN_SETUP_FAILED(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

}
