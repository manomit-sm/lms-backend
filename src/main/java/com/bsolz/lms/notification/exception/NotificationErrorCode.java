package com.bsolz.lms.notification.exception;

import com.bsolz.lms.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND),
	NOT_A_USER(HttpStatus.FORBIDDEN);

	private final HttpStatus status;

}
