package com.bsolz.lms.reporting.exception;

import com.bsolz.lms.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ReportErrorCode implements ErrorCode {

	INVALID_RANGE(HttpStatus.BAD_REQUEST),
	NO_LEAVE_PERIOD(HttpStatus.CONFLICT),
	EMPLOYEE_NOT_VISIBLE(HttpStatus.FORBIDDEN),
	NOT_LINKED_TO_EMPLOYEE(HttpStatus.FORBIDDEN),
	EXPORT_NOT_FOUND(HttpStatus.NOT_FOUND),
	EXPORT_NOT_READY(HttpStatus.CONFLICT),
	EXPORT_TOO_LARGE(HttpStatus.UNPROCESSABLE_CONTENT);

	private final HttpStatus status;

}
