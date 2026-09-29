package com.bsolz.lms.leave.exception;

import com.bsolz.lms.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * Leave errors. The 422 codes are leave-rule violations: a preview lists them all, a submission fails
 * with the first and lists all under {@code violations}.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum LeaveErrorCode implements ErrorCode {

	LEAVE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND),
	ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
	LEAVE_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND),
	NOT_LINKED_TO_EMPLOYEE(HttpStatus.FORBIDDEN),
	NOT_REQUESTER(HttpStatus.FORBIDDEN),
	INVALID_DATES(HttpStatus.BAD_REQUEST),
	INVALID_SESSIONS(HttpStatus.BAD_REQUEST),
	UNSUPPORTED_ATTACHMENT(HttpStatus.BAD_REQUEST),

	LEAVE_TYPE_INACTIVE(HttpStatus.UNPROCESSABLE_CONTENT),
	HALF_DAY_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT),
	EMPLOYEE_EXITED(HttpStatus.UNPROCESSABLE_CONTENT),
	BEFORE_JOINING(HttpStatus.UNPROCESSABLE_CONTENT),
	NO_LEAVE_PERIOD(HttpStatus.UNPROCESSABLE_CONTENT),
	CROSSES_LEAVE_PERIOD(HttpStatus.UNPROCESSABLE_CONTENT),
	LEAVE_PERIOD_CLOSED(HttpStatus.UNPROCESSABLE_CONTENT),
	NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_CONTENT),
	NO_WORKING_DAYS(HttpStatus.UNPROCESSABLE_CONTENT),
	BACKDATING_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT),
	INSUFFICIENT_NOTICE(HttpStatus.UNPROCESSABLE_CONTENT),
	MAX_CONSECUTIVE_DAYS_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT),
	NOT_ALLOWED_DURING_PROBATION(HttpStatus.UNPROCESSABLE_CONTENT),
	ATTACHMENT_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT),
	ATTACHMENT_NOT_UPLOADED(HttpStatus.UNPROCESSABLE_CONTENT),

	INSUFFICIENT_BALANCE(HttpStatus.CONFLICT),
	OVERLAPPING_LEAVE(HttpStatus.CONFLICT),
	INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT);

	private final HttpStatus status;

}
