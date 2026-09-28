package com.bsolz.lms.shared.exception;

import java.util.Map;
import lombok.Getter;

/**
 * A business error with a stable {@link ErrorCode}; rendered by {@link GlobalExceptionHandler} as
 * {@code application/problem+json} with the code's HTTP status. The message becomes the problem
 * {@code detail}, so it must be safe to show to API clients. {@code properties} are added to the
 * problem as extra members (e.g. per-row errors of an import).
 */
@Getter
public class ApiException extends RuntimeException {

	private final ErrorCode errorCode;

	private final Map<String, Object> properties;

	public ApiException(ErrorCode errorCode, String message) {
		this(errorCode, message, Map.of());
	}

	public ApiException(ErrorCode errorCode, String message, Map<String, Object> properties) {
		super(message);
		this.errorCode = errorCode;
		this.properties = Map.copyOf(properties);
	}

}
