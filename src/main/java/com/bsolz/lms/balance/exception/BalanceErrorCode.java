package com.bsolz.lms.balance.exception;

import com.bsolz.lms.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum BalanceErrorCode implements ErrorCode {

	BALANCE_NOT_FOUND(HttpStatus.NOT_FOUND),
	EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND),
	LEAVE_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND),
	LEAVE_PERIOD_NOT_FOUND(HttpStatus.NOT_FOUND),
	NO_LEAVE_PERIOD(HttpStatus.CONFLICT),
	LEAVE_PERIOD_CLOSED(HttpStatus.CONFLICT),
	LEAVE_TYPE_NOT_TRACKED(HttpStatus.CONFLICT),
	NOT_ELIGIBLE(HttpStatus.CONFLICT),
	INSUFFICIENT_BALANCE(HttpStatus.CONFLICT),
	ALREADY_HELD(HttpStatus.CONFLICT),
	INVALID_AMOUNT(HttpStatus.BAD_REQUEST);

	private final HttpStatus status;

}
