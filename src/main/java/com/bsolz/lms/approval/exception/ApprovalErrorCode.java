package com.bsolz.lms.approval.exception;

import com.bsolz.lms.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ApprovalErrorCode implements ErrorCode {

	WORKFLOW_NOT_FOUND(HttpStatus.NOT_FOUND),
	TASK_NOT_FOUND(HttpStatus.NOT_FOUND),
	NOT_ASSIGNED(HttpStatus.FORBIDDEN),
	TASK_NOT_PENDING(HttpStatus.CONFLICT),
	APPROVAL_ALREADY_PENDING(HttpStatus.CONFLICT),
	WORKFLOW_NAME_TAKEN(HttpStatus.CONFLICT),
	WORKFLOW_PRIORITY_TAKEN(HttpStatus.CONFLICT),
	DEFAULT_WORKFLOW_FIXED(HttpStatus.CONFLICT),
	INVALID_WORKFLOW(HttpStatus.BAD_REQUEST),
	COMMENT_REQUIRED(HttpStatus.BAD_REQUEST);

	private final HttpStatus status;

}
