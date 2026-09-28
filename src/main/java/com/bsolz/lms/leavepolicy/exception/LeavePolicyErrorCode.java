package com.bsolz.lms.leavepolicy.exception;

import com.bsolz.lms.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum LeavePolicyErrorCode implements ErrorCode {

	LEAVE_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND),
	LEAVE_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND),
	LEAVE_PERIOD_NOT_FOUND(HttpStatus.NOT_FOUND),
	LEAVE_TYPE_CODE_TAKEN(HttpStatus.CONFLICT),
	LEAVE_TYPE_NAME_TAKEN(HttpStatus.CONFLICT),
	LEAVE_POLICY_NAME_TAKEN(HttpStatus.CONFLICT),
	LEAVE_PERIOD_NAME_TAKEN(HttpStatus.CONFLICT),
	LEAVE_PERIOD_OVERLAP(HttpStatus.CONFLICT),
	POLICY_CONFLICT(HttpStatus.CONFLICT),
	POLICY_LEAVE_TYPE_IMMUTABLE(HttpStatus.CONFLICT),
	INVALID_LEAVE_PERIOD(HttpStatus.BAD_REQUEST),
	INVALID_EFFECTIVE_DATES(HttpStatus.BAD_REQUEST),
	UNKNOWN_ORG_UNIT(HttpStatus.BAD_REQUEST);

	private final HttpStatus status;

}
