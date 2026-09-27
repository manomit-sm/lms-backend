package com.bsolz.lms.organization.exception;

import com.bsolz.lms.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum OrganizationErrorCode implements ErrorCode {

	WORK_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND),
	LOCATION_NOT_FOUND(HttpStatus.NOT_FOUND),
	DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
	DESIGNATION_NOT_FOUND(HttpStatus.NOT_FOUND),
	EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND),
	WORK_SCHEDULE_NAME_TAKEN(HttpStatus.CONFLICT),
	LOCATION_CODE_TAKEN(HttpStatus.CONFLICT),
	DEPARTMENT_CODE_TAKEN(HttpStatus.CONFLICT),
	DESIGNATION_NAME_TAKEN(HttpStatus.CONFLICT),
	EMPLOYEE_CODE_TAKEN(HttpStatus.CONFLICT),
	EMPLOYEE_EMAIL_TAKEN(HttpStatus.CONFLICT),
	DEFAULT_WORK_SCHEDULE_REQUIRED(HttpStatus.CONFLICT),
	DEPARTMENT_CYCLE(HttpStatus.CONFLICT),
	REPORTING_CYCLE(HttpStatus.CONFLICT),
	EMPLOYEE_HAS_REPORTS(HttpStatus.CONFLICT),
	EMPLOYEE_EXITED(HttpStatus.CONFLICT),
	INVALID_EMPLOYMENT_STATUS(HttpStatus.BAD_REQUEST),
	INVALID_TIMEZONE(HttpStatus.BAD_REQUEST),
	INVALID_DATES(HttpStatus.BAD_REQUEST);

	private final HttpStatus status;

}
