package com.bsolz.lms.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * A stable, machine-readable error code returned as {@code errorCode} in every
 * {@code application/problem+json} response. Each module declares its own enum implementing this.
 */
public interface ErrorCode {

	String name();

	HttpStatus status();

	default String code() {
		return name();
	}

}
