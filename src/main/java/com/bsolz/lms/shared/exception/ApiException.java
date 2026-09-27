package com.bsolz.lms.shared.exception;

import lombok.Getter;

/**
 * A business error with a stable {@link ErrorCode}; rendered by {@link GlobalExceptionHandler} as
 * {@code application/problem+json} with the code's HTTP status. The message becomes the problem
 * {@code detail}, so it must be safe to show to API clients.
 */
@Getter
public class ApiException extends RuntimeException {

	private final ErrorCode errorCode;

	public ApiException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

}
