package com.bsolz.lms.shared.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

	BAD_REQUEST(HttpStatus.BAD_REQUEST),
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
	NOT_FOUND(HttpStatus.NOT_FOUND),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
	CONFLICT(HttpStatus.CONFLICT),
	CONCURRENT_UPDATE(HttpStatus.CONFLICT),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	/** Fallback code for framework-raised errors that carry only an HTTP status. */
	public static CommonErrorCode forStatus(HttpStatusCode status) {
		return switch (status.value()) {
			case 404 -> NOT_FOUND;
			case 405 -> METHOD_NOT_ALLOWED;
			case 409 -> CONFLICT;
			case 415 -> UNSUPPORTED_MEDIA_TYPE;
			default -> status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
		};
	}

}
