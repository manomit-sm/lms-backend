package com.bsolz.lms.calendar.exception;

import com.bsolz.lms.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CalendarErrorCode implements ErrorCode {

	INVALID_SCOPE(HttpStatus.BAD_REQUEST),
	INVALID_RANGE(HttpStatus.BAD_REQUEST),
	NOT_LINKED_TO_EMPLOYEE(HttpStatus.FORBIDDEN),
	DEPARTMENT_NOT_VISIBLE(HttpStatus.FORBIDDEN);

	private final HttpStatus status;

}
