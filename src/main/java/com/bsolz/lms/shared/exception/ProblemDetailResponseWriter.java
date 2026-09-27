package com.bsolz.lms.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes a problem+json body directly to the servlet response, for errors raised in servlet
 * filters (authentication, tenant resolution) where Spring MVC's exception handling does not run.
 * Produces the same shape as {@link GlobalExceptionHandler}.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailResponseWriter {

	private final JsonMapper jsonMapper;

	public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode, String detail)
			throws IOException {
		ProblemDetail problem = ProblemDetails.of(errorCode, detail);
		Map<String, Object> body = new LinkedHashMap<>();
		if (problem.getType() != null) {
			body.put("type", problem.getType().toString());
		}
		body.put("title", problem.getTitle());
		body.put("status", problem.getStatus());
		body.put("detail", problem.getDetail());
		body.put("instance", request.getRequestURI());
		if (problem.getProperties() != null) {
			body.putAll(problem.getProperties());
		}
		response.setStatus(errorCode.status().value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		jsonMapper.writeValue(response.getOutputStream(), body);
	}

}
