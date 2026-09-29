package com.bsolz.lms.holiday.exception;

import com.bsolz.lms.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum HolidayErrorCode implements ErrorCode {

	HOLIDAY_NOT_FOUND(HttpStatus.NOT_FOUND),
	HOLIDAY_ALREADY_EXISTS(HttpStatus.CONFLICT),
	UNKNOWN_ORG_UNIT(HttpStatus.BAD_REQUEST),
	INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST),
	INVALID_HOLIDAY_IMPORT(HttpStatus.BAD_REQUEST);

	private final HttpStatus status;

}
