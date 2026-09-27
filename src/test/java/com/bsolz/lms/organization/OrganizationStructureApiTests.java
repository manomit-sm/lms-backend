package com.bsolz.lms.organization;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** Departments, designations, locations and work schedules. */
@IntegrationTest
class OrganizationStructureApiTests {

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
		tenant = fixtures.newTenant("org");
		admin = fixtures.adminBearer(tenant);
	}

	@Test
	void managesDepartmentTree() throws Exception {
		String engineering = id(send(post("/api/v1/departments"), department("ENG", "Engineering", null))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.active").value(true)));
		String backend = id(send(post("/api/v1/departments"), department("BE", "Backend", engineering))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.parentDepartmentId").value(engineering)));

		send(post("/api/v1/departments"), department("eng", "Duplicate", null))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("DEPARTMENT_CODE_TAKEN"));
		send(put("/api/v1/departments/{id}", engineering), department("ENG", "Engineering", backend))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("DEPARTMENT_CYCLE"));
		send(put("/api/v1/departments/{id}", backend), department("BE", "Backend Platform", engineering))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Backend Platform"));

		mockMvc.perform(get("/api/v1/departments").header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].code", containsInAnyOrder("ENG", "BE")));
	}

	@Test
	void managesDesignationsAndLocations() throws Exception {
		send(post("/api/v1/designations"), Map.of("name", "Senior Engineer", "level", 3))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.level").value(3));
		send(post("/api/v1/designations"), Map.of("name", "senior engineer"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("DESIGNATION_NAME_TAKEN"));

		send(post("/api/v1/locations"),
				Map.of("code", "BLR", "name", "Bengaluru", "countryCode", "IN", "timezone", "Asia/Kolkata"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.timezone").value("Asia/Kolkata"));
		send(post("/api/v1/locations"),
				Map.of("code", "XXX", "name", "Nowhere", "countryCode", "XX", "timezone", "Nowhere/City"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_TIMEZONE"));
		send(post("/api/v1/locations"),
				Map.of("code", "BAD", "name", "Bad", "countryCode", "india", "timezone", "UTC"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("countryCode"));
	}

	@Test
	void keepsExactlyOneDefaultWorkSchedule() throws Exception {
		String seededDefault = jsonMapper.readTree(mockMvc
				.perform(get("/api/v1/work-schedules").header("Authorization", admin))
				.andExpect(jsonPath("$[0].defaultSchedule").value(true))
				.andExpect(jsonPath("$[0].workingDays", containsInAnyOrder("MONDAY", "TUESDAY", "WEDNESDAY",
						"THURSDAY", "FRIDAY")))
				.andReturn().getResponse().getContentAsString()).get(0).get("id").asString();

		String sixDay = id(send(post("/api/v1/work-schedules"), Map.of("name", "Six day", "workingDays",
				List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"), "defaultSchedule", true))
				.andExpect(status().isCreated()));

		mockMvc.perform(get("/api/v1/work-schedules/{id}", seededDefault).header("Authorization", admin))
				.andExpect(jsonPath("$.defaultSchedule").value(false));
		send(put("/api/v1/work-schedules/{id}", sixDay), Map.of("name", "Six day", "workingDays",
				List.of("MONDAY"), "defaultSchedule", false))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("DEFAULT_WORK_SCHEDULE_REQUIRED"));
		send(post("/api/v1/work-schedules"), Map.of("name", "Nothing", "workingDays", List.of()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void everyoneReadsButOnlyOrganizationManagersChange() throws Exception {
		String email = fixtures.createEmployee(tenant, "Reader", fixtures.createDepartment(tenant), null).email();
		String employee = fixtures.bearer(tenant, fixtures.awaitSubject(tenant, email));

		mockMvc.perform(get("/api/v1/departments").header("Authorization", employee))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].active", hasItem(true)));
		mockMvc.perform(post("/api/v1/departments").header("Authorization", employee)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(department("HACK", "Hack", null))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
	}

	private static Map<String, Object> department(String code, String name, String parentId) {
		Map<String, Object> body = new HashMap<>();
		body.put("code", code);
		body.put("name", name);
		body.put("parentDepartmentId", parentId);
		return body;
	}

	private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
		return mockMvc.perform(request.header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(body)));
	}

	private String id(ResultActions result) throws Exception {
		return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asString();
	}

}
