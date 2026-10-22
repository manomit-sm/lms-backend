package com.bsolz.lms.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** What the frontend relies on: error shapes, CORS and the OpenAPI document. */
@IntegrationTest
class ApiContractTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	JsonMapper jsonMapper;

	TestTenant tenant;

	String admin;

	@BeforeEach
	void setUp() {
		tenant = fixtures.newTenant("api");
		admin = fixtures.adminBearer(tenant);
	}

	@Test
	void badInputIsReportedPerField() throws Exception {
		mockMvc.perform(get("/api/v1/reports/balances?leavePeriodId=nope").header("Authorization", admin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("leavePeriodId"))
				.andExpect(jsonPath("$.errors[0].message").value("must be a UUID"));
		mockMvc.perform(get("/api/v1/reports/decisions?from=2026-01-01&to=soon").header("Authorization", admin))
				.andExpect(jsonPath("$.errors[0].field").value("to"))
				.andExpect(jsonPath("$.errors[0].message").value("must be a date (yyyy-MM-dd)"));
		mockMvc.perform(get("/api/v1/audit-logs?action=NOPE").header("Authorization", admin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("action"));
		mockMvc.perform(get("/api/v1/calendar/availability").header("Authorization", admin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("date"))
				.andExpect(jsonPath("$.errors[0].message").value("is required"));

		mockMvc.perform(post("/api/v1/reports/exports").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"report\": \"BALANCES\", \"format\": \"PDF\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("format"))
				.andExpect(jsonPath("$.errors[0].message").value("must be one of [CSV, XLSX]"));
		mockMvc.perform(post("/api/v1/reports/exports").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"report\": "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
				.andExpect(jsonPath("$.detail").value("The request body is not valid JSON"));
	}

	@Test
	void theFrontendsOriginMayCallTheApi() throws Exception {
		mockMvc.perform(options("/api/v1/auth/me").header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", admin).header("Origin", "http://localhost:5173"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
		mockMvc.perform(options("/api/v1/auth/me").header("Origin", "https://evil.example")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden());
	}

	@Test
	void theOpenApiDocumentDescribesAuthenticationAndErrors() throws Exception {
		JsonNode tenantApi = jsonMapper.readTree(mockMvc.perform(get("/v3/api-docs/tenant"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(tenantApi.get("components").get("securitySchemes").get("bearer").get("scheme").asString())
				.isEqualTo("bearer");
		assertThat(tenantApi.get("security").get(0).has("bearer")).isTrue();
		assertThat(tenantApi.get("paths").has("/api/v1/reports/exports")).isTrue();
		assertThat(tenantApi.get("paths").has("/platform/tenants")).isFalse();
		JsonNode problem = tenantApi.get("components").get("schemas").get("ProblemDetail");
		assertThat(problem.get("properties").get("errorCode").get("enum").valueStream().map(JsonNode::asString))
				.contains("VALIDATION_FAILED", "ACCESS_DENIED", "INSUFFICIENT_BALANCE", "OVERLAPPING_LEAVE",
						"EXPORT_NOT_FOUND");
		// Import problems (e.g. holidays from CSV) say which row is wrong
		assertThat(problem.get("properties").get("errors").get("items").get("properties").propertyNames())
				.contains("field", "row", "message");
		assertThat(tenantApi.get("paths").get("/api/v1/leave-requests").get("post").get("responses").has("409"))
				.isTrue();
		JsonNode activityDetails = tenantApi.get("components").get("schemas").get("ActivityResponse").get("properties")
				.get("details");
		assertThat(activityDetails.get("type").asString()).isEqualTo("object");
		assertThat(activityDetails.get("additionalProperties").asBoolean()).isTrue();
		assertThat(tenantApi.get("components").get("schemas").has("JsonNode")).isFalse();

		mockMvc.perform(get("/v3/api-docs/platform"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths").isMap())
				.andExpect(jsonPath("$.paths.['/platform/tenants']").exists());
		mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
		mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
	}

	/** Generated clients send what the document says: {@code ?page=&size=&sort=}, not a {@code pageable} object. */
	@Test
	void pagedEndpointsDocumentPageSizeAndSort() throws Exception {
		for (String group : new String[] { "tenant", "platform" }) {
			JsonNode paths = jsonMapper.readTree(mockMvc.perform(get("/v3/api-docs/" + group))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("paths");
			paths.forEachEntry((path, operations) -> operations.forEachEntry((method, operation) -> {
				if (operation.has("parameters")) {
					assertThat(operation.get("parameters").valueStream().map(parameter -> parameter.get("name").asString()))
							.as(method + " " + path).doesNotContain("pageable");
				}
			}));
		}
		JsonNode employees = jsonMapper.readTree(mockMvc.perform(get("/v3/api-docs/tenant"))
				.andReturn().getResponse().getContentAsString()).get("paths").get("/api/v1/employees").get("get");
		assertThat(employees.get("parameters").valueStream().map(parameter -> parameter.get("name").asString()))
				.contains("page", "size", "sort");
	}

	/**
	 * The OpenAPI document names schemas by simple class name, so two DTO records with the same name but
	 * different components would silently share one schema - and the generated client would miss fields.
	 */
	@Test
	void dtoRecordsWithTheSameNameHaveTheSameShape() {
		Map<String, List<Class<?>>> bySimpleName = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("com.bsolz.lms").stream()
				.filter(javaClass -> javaClass.getPackageName().contains(".web.dto"))
				.map(JavaClass::reflect)
				.filter(Class::isRecord)
				.collect(Collectors.groupingBy(Class::getSimpleName));
		bySimpleName.forEach((name, records) -> assertThat(records.stream().map(ApiContractTests::shape).distinct())
				.as("records named %s: %s", name, records)
				.hasSize(1));
	}

	private static String shape(Class<?> record) {
		return Arrays.stream(record.getRecordComponents())
				.map(component -> component.getName() + ":" + simpleType(component))
				.collect(Collectors.joining(","));
	}

	/** Nested types by simple name too, as the schema refers to them. */
	private static String simpleType(RecordComponent component) {
		return component.getGenericType().getTypeName().replaceAll("[\\w.]+[.$]", "");
	}

}
