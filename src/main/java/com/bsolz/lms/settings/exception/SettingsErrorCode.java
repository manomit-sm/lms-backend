package com.bsolz.lms.settings.exception;

import com.bsolz.lms.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum SettingsErrorCode implements ErrorCode {

	INVALID_TIMEZONE(HttpStatus.BAD_REQUEST),
	UNSUPPORTED_DATE_FORMAT(HttpStatus.BAD_REQUEST);

	private final HttpStatus status;

}
