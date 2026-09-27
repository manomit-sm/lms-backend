package com.bsolz.lms.shared.exception;

import com.bsolz.lms.shared.security.SecurityErrorCode;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;


/**
 * Renders every error raised inside Spring MVC as {@code application/problem+json} with a stable
 * {@code errorCode}. Framework errors handled by {@link ResponseEntityExceptionHandler} get a
 * status-derived code (see {@link #createResponseEntity}).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
		return problem(ex.getErrorCode(), ex.getMessage());
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
		return problem(SecurityErrorCode.ACCESS_DENIED, "You do not have permission to perform this action");
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
		return problem(SecurityErrorCode.UNAUTHENTICATED, "Authentication is required");
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
		ProblemDetail problem = ProblemDetails.of(CommonErrorCode.VALIDATION_FAILED, "Request validation failed");
		problem.setProperty("errors", ex.getConstraintViolations().stream()
				.map(violation -> fieldError(violation.getPropertyPath().toString(), violation.getMessage()))
				.toList());
		return ResponseEntity.status(problem.getStatus()).body(problem);
	}

	@ExceptionHandler(PropertyReferenceException.class)
	ResponseEntity<ProblemDetail> handlePropertyReference(PropertyReferenceException ex) {
		return problem(CommonErrorCode.BAD_REQUEST, "Unknown sort property: " + ex.getPropertyName());
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return problem(CommonErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ProblemDetails.of(CommonErrorCode.VALIDATION_FAILED, "Request validation failed");
		List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> fieldError(error.getField(), error.getDefaultMessage()))
				.toList();
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, CommonErrorCode.VALIDATION_FAILED.status(), request);
	}

	@Override
	protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
			WebRequest request) {
		if (body instanceof ProblemDetail problem
				&& (problem.getProperties() == null || !problem.getProperties().containsKey(ProblemDetails.ERROR_CODE))) {
			problem.setProperty(ProblemDetails.ERROR_CODE, CommonErrorCode.forStatus(statusCode).code());
		}
		return super.createResponseEntity(body, headers, statusCode, request);
	}

	private static ResponseEntity<ProblemDetail> problem(ErrorCode errorCode, String detail) {
		return ResponseEntity.status(errorCode.status()).body(ProblemDetails.of(errorCode, detail));
	}

	private static Map<String, String> fieldError(String field, String message) {
		return Map.of("field", field, "message", Objects.requireNonNullElse(message, "is invalid"));
	}

}
