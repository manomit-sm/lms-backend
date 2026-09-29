package com.bsolz.lms.shared.config;

import com.bsolz.lms.shared.exception.ErrorCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * The OpenAPI document - the frontend's contract. Two groups: the tenant API ({@code /api/**}, tokens
 * from the tenant user pool) and the platform API ({@code /platform/**}, platform admins). Every
 * operation needs a bearer access token and documents the {@code application/problem+json} error
 * responses; the {@code ProblemDetail} schema lists every {@code errorCode} the API can return.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	static final String BEARER = "bearer";

	static final String PROBLEM = "ProblemDetail";

	private static final String BASE_PACKAGE = "com.bsolz.lms";

	private static final Map<String, String> ERROR_RESPONSES = Map.of(
			"400", "Invalid request: `VALIDATION_FAILED` lists the fields in `errors`",
			"401", "Missing, expired or invalid access token",
			"403", "Not allowed, or the tenant is unknown or suspended",
			"404", "Not found (or not visible to the caller)",
			"409", "Conflicts with the current state (e.g. overlapping leave, insufficient balance)",
			"422", "Breaks a business rule; `violations` lists every rule broken",
			"503", "The tenant is temporarily unavailable (e.g. its schema is being migrated)");

	@Bean
	OpenAPI lmsOpenApi() {
		return new OpenAPI()
				.info(new Info().title("Leave Management API").version("v1").description("""
						Multi-tenant leave management. Authenticate with an access token from the tenant user pool \
						(`Authorization: Bearer <token>`); the tenant is taken from the token's `tenant_id` claim. \
						Errors are `application/problem+json` with a stable `errorCode` (see the `ProblemDetail` \
						schema)."""))
				.components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}

	@Bean
	GroupedOpenApi tenantApi() {
		return GroupedOpenApi.builder().group("tenant").displayName("Tenant API").pathsToMatch("/api/**").build();
	}

	@Bean
	GroupedOpenApi platformApi() {
		return GroupedOpenApi.builder().group("platform").displayName("Platform API").pathsToMatch("/platform/**")
				.build();
	}

	/** Adds the problem schema with every error code, and the error responses, to every group. */
	@Bean
	GlobalOpenApiCustomizer problemResponses() {
		List<Map<String, Object>> codes = errorCodes();
		return openApi -> {
			if (openApi.getComponents() == null) {
				openApi.setComponents(new Components());
			}
			openApi.getComponents().addSchemas(PROBLEM, problemSchema(codes));
			if (openApi.getPaths() == null) {
				return;
			}
			openApi.getPaths().values().forEach(path -> path.readOperations().forEach(OpenApiConfig::addErrors));
		};
	}

	private static void addErrors(Operation operation) {
		if (operation.getResponses() == null) {
			operation.setResponses(new ApiResponses());
		}
		new TreeMap<>(ERROR_RESPONSES).forEach((status, description) -> operation.getResponses().computeIfAbsent(status,
				ignored -> new ApiResponse().description(description).content(new Content().addMediaType(
						"application/problem+json",
						new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM))))));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Schema<?> problemSchema(List<Map<String, Object>> codes) {
		Schema<String> errorCode = new StringSchema().description("Stable, machine-readable error code");
		errorCode.setEnum(codes.stream().map(code -> (String) code.get("code")).toList());
		Schema fieldError = new ObjectSchema().addProperty("field", new StringSchema())
				.addProperty("message", new StringSchema());
		Schema violation = new ObjectSchema().addProperty("code", new StringSchema())
				.addProperty("message", new StringSchema());
		Schema problem = new ObjectSchema()
				.description("RFC 9457 problem details. `x-error-codes` gives each code's HTTP status.")
				.addProperty("type", new StringSchema())
				.addProperty("title", new StringSchema())
				.addProperty("status", new IntegerSchema())
				.addProperty("detail", new StringSchema().description("Human-readable; safe to show to the user"))
				.addProperty("instance", new StringSchema())
				.addProperty("errorCode", errorCode)
				.addProperty("errors", new ArraySchema().items(fieldError).description("For VALIDATION_FAILED"))
				.addProperty("violations", new ArraySchema().items(violation)
						.description("For leave rule violations: every rule the request breaks"));
		problem.addExtension("x-error-codes", codes);
		return problem;
	}

	/** Every {@link ErrorCode} enum constant in the application, with its HTTP status. */
	private static List<Map<String, Object>> errorCodes() {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AssignableTypeFilter(ErrorCode.class));
		Map<String, Map<String, Object>> codes = new LinkedHashMap<>();
		for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
			Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), OpenApiConfig.class.getClassLoader());
			if (!type.isEnum()) {
				continue;
			}
			for (Object constant : type.getEnumConstants()) {
				ErrorCode code = (ErrorCode) constant;
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("code", code.code());
				entry.put("status", code.status().value());
				codes.putIfAbsent(code.code(), entry);
			}
		}
		List<Map<String, Object>> sorted = new ArrayList<>(codes.values());
		sorted.sort(Comparator.comparing(code -> (String) code.get("code")));
		return sorted;
	}

}
