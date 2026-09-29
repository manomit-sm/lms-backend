package com.bsolz.lms.shared.exception;

import com.bsolz.lms.shared.security.SecurityErrorCode;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

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
		ProblemDetail problem = ProblemDetails.of(ex.getErrorCode(), ex.getMessage());
		ex.getProperties().forEach(problem::setProperty);
		return ResponseEntity.status(problem.getStatus()).body(problem);
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

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<ProblemDetail> handleOptimisticLocking(OptimisticLockingFailureException ex) {
		return problem(CommonErrorCode.CONCURRENT_UPDATE, "Someone else changed this at the same time; reload and try again");
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

	/** A path or query value of the wrong type, e.g. a malformed UUID or an unknown enum constant. */
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String name = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName()
				: Objects.requireNonNullElse(ex.getPropertyName(), "value");
		return validationFailed(ex, List.of(fieldError(name, expected(ex.getRequiredType()))), headers, request);
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return validationFailed(ex, List.of(fieldError(ex.getParameterName(), "is required")), headers, request);
	}

	/** Constraint annotations on {@code @RequestParam}/{@code @PathVariable} parameters. */
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream().map(error -> fieldError(
						Objects.requireNonNullElse(result.getMethodParameter().getParameterName(), "value"),
						error.getDefaultMessage())))
				.toList();
		return validationFailed(ex, errors, headers, request);
	}

	/**
	 * An unreadable body: malformed JSON, or a value of the wrong type (e.g. an unknown enum constant) -
	 * reported with the field's path, never with the parser's internals.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		if (ex.getMostSpecificCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
			String field = mismatch.getPath().stream()
					.map(reference -> reference.getPropertyName() != null ? reference.getPropertyName()
							: "[" + reference.getIndex() + "]")
					.reduce((a, b) -> b.startsWith("[") ? a + b : a + "." + b)
					.orElse("body");
			String message = mismatch instanceof UnrecognizedPropertyException ? "is not a known field"
					: expected(mismatch.getTargetType());
			return validationFailed(ex, List.of(fieldError(field, message)), headers, request);
		}
		ProblemDetail problem = ProblemDetails.of(CommonErrorCode.BAD_REQUEST, "The request body is not valid JSON");
		return handleExceptionInternal(ex, problem, headers, CommonErrorCode.BAD_REQUEST.status(), request);
	}

	/** A database constraint the service didn't check first (e.g. two concurrent creates of the same code). */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
		return problem(CommonErrorCode.CONFLICT, "The change conflicts with existing data; reload and try again");
	}

	private ResponseEntity<Object> validationFailed(Exception ex, List<Map<String, String>> errors, HttpHeaders headers,
			WebRequest request) {
		ProblemDetail problem = ProblemDetails.of(CommonErrorCode.VALIDATION_FAILED, "Request validation failed");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, CommonErrorCode.VALIDATION_FAILED.status(), request);
	}

	private static String expected(Class<?> type) {
		if (type == null) {
			return "is invalid";
		}
		if (type.isEnum()) {
			return "must be one of " + Arrays.stream(type.getEnumConstants()).map(Object::toString).toList();
		}
		if (type == UUID.class) {
			return "must be a UUID";
		}
		if (type == LocalDate.class) {
			return "must be a date (yyyy-MM-dd)";
		}
		if (Number.class.isAssignableFrom(type) || type.isPrimitive() && type != boolean.class) {
			return "must be a number";
		}
		if (type == Boolean.class || type == boolean.class) {
			return "must be true or false";
		}
		return "is invalid";
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
