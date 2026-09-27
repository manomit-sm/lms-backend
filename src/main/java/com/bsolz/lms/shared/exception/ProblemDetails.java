package com.bsolz.lms.shared.exception;

import org.springframework.http.ProblemDetail;

public final class ProblemDetails {

	public static final String ERROR_CODE = "errorCode";

	private ProblemDetails() {
	}

	public static ProblemDetail of(ErrorCode errorCode, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
		problem.setProperty(ERROR_CODE, errorCode.code());
		return problem;
	}

}
