package com.bsolz.lms.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.identity.idp.FakeIdentityProvider;
import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class EmployeeApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	FakeIdentityProvider identityProvider;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	JsonMapper jsonMapper;

	TestTenant tenant;

	String admin;

	UUID department;

	@BeforeEach
	void setUp() {
		tenant = fixtures.newTenant("emp");
		admin = fixtures.adminBearer(tenant);
		department = fixtures.createDepartment(tenant);
	}

	@Test
	void creatingAnEmployeeInvitesThemAsAUser() throws Exception {
		String email = TestFixtures.unique("new") + "@Example.test";
		send(post("/api/v1/employees"), admin, employee("E-" + TestFixtures.unique("x"), "Nina", email, null))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email.toLowerCase()))
				.andExpect(jsonPath("$.department.id").value(department.toString()))
				.andExpect(jsonPath("$.employmentStatus").value("ACTIVE"));

		String subject = fixtures.awaitSubject(tenant, email);
		assertThat(identityProvider.find(email)).isPresent();
		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", fixtures.bearer(tenant, subject)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles", containsInAnyOrder("EMPLOYEE")))
				.andExpect(jsonPath("$.employee.firstName").value("Nina"));
		mockMvc.perform(get("/api/v1/employees/me").header("Authorization", fixtures.bearer(tenant, subject)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName").value("Nina"));

		// The event went through the tenant's own outbox and was marked complete there - never via public.
		String completedInTenant = "SELECT count(*) FROM event_publication WHERE event_type LIKE '%EmployeeCreated'"
				+ " AND serialized_event LIKE '%" + email.toLowerCase() + "%' AND completion_date IS NOT NULL";
		await().atMost(Duration.ofSeconds(15)).until(() -> TenantContext.call(tenant.info(),
				() -> jdbcTemplate.queryForObject(completedInTenant, Integer.class)) == 1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM public.event_publication WHERE event_type LIKE '%EmployeeCreated'", Integer.class))
				.isZero();
	}

	@Test
	void validatesEmployees() throws Exception {
		String email = TestFixtures.unique("dup") + "@example.test";
		send(post("/api/v1/employees"), admin, employee("DUP-1" + TestFixtures.unique(""), "Dup", email, null))
				.andExpect(status().isCreated());

		send(post("/api/v1/employees"), admin, employee(TestFixtures.unique("C"), "Dup", email.toUpperCase(), null))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("EMPLOYEE_EMAIL_TAKEN"));
		Map<String, Object> exited = employee(TestFixtures.unique("C"), "Gone", TestFixtures.unique("g") + "@x.test", null);
		exited.put("employmentStatus", "EXITED");
		send(post("/api/v1/employees"), admin, exited)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_EMPLOYMENT_STATUS"));
		Map<String, Object> noDepartment = employee(TestFixtures.unique("C"), "Lost", TestFixtures.unique("l") + "@x.test", null);
		noDepartment.remove("departmentId");
		send(post("/api/v1/employees"), admin, noDepartment)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("departmentId"));
	}

	@Test
	void everyoneSeesOnlyTheirDataScope() throws Exception {
		// maya → arun → bea;  carl is outside the line
		EmployeeResponse maya = fixtures.createEmployee(tenant, "Maya", department, null);
		EmployeeResponse arun = fixtures.createEmployee(tenant, "Arun", department, maya.id());
		EmployeeResponse bea = fixtures.createEmployee(tenant, "Bea", department, arun.id());
		EmployeeResponse carl = fixtures.createEmployee(tenant, "Carl", department, null);
		String mayaToken = tokenFor(maya);
		String arunToken = tokenFor(arun);
		String beaToken = tokenFor(bea);
		String carlToken = tokenFor(carl);
		fixtures.grantRoles(tenant, maya.email(), "MANAGER");
		fixtures.grantRoles(tenant, arun.email(), "MANAGER");

		listNames(mayaToken).andExpect(jsonPath("$.content[*].firstName", containsInAnyOrder("Maya", "Arun", "Bea")));
		listNames(arunToken).andExpect(jsonPath("$.content[*].firstName", containsInAnyOrder("Arun", "Bea")));
		listNames(beaToken).andExpect(jsonPath("$.content[*].firstName", containsInAnyOrder("Bea")));
		listNames(admin).andExpect(jsonPath("$.content[*].firstName", hasItems("Maya", "Arun", "Bea", "Carl")));

		mockMvc.perform(get("/api/v1/employees/{id}", bea.id()).header("Authorization", mayaToken))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/employees/{id}", arun.id()).header("Authorization", carlToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/employees/{id}", maya.id()).header("Authorization", beaToken))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/employees/me/team").header("Authorization", mayaToken))
				.andExpect(jsonPath("$[*].firstName", containsInAnyOrder("Arun")));
		mockMvc.perform(get("/api/v1/employees/me/team?includeIndirect=true").header("Authorization", mayaToken))
				.andExpect(jsonPath("$[*].firstName", containsInAnyOrder("Arun", "Bea")));
	}

	@Test
	void preventsReportingCycles() throws Exception {
		EmployeeResponse maya = fixtures.createEmployee(tenant, "Maya", department, null);
		EmployeeResponse arun = fixtures.createEmployee(tenant, "Arun", department, maya.id());
		EmployeeResponse bea = fixtures.createEmployee(tenant, "Bea", department, arun.id());

		send(put("/api/v1/employees/{id}", maya.id()), admin, update(maya, bea.id()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("REPORTING_CYCLE"));
		send(put("/api/v1/employees/{id}", maya.id()), admin, update(maya, maya.id()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("REPORTING_CYCLE"));
		send(put("/api/v1/employees/{id}", bea.id()), admin, update(bea, maya.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reportingManager.name").value(maya.fullName()));
	}

	@Test
	void exitingAnEmployeeDisablesTheirUser() throws Exception {
		EmployeeResponse maya = fixtures.createEmployee(tenant, "Maya", department, null);
		EmployeeResponse arun = fixtures.createEmployee(tenant, "Arun", department, maya.id());
		String arunToken = tokenFor(arun);

		send(post("/api/v1/employees/{id}/exit", maya.id()), admin, Map.of("exitDate", "2026-12-31"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("EMPLOYEE_HAS_REPORTS"));
		send(post("/api/v1/employees/{id}/exit", arun.id()), admin, Map.of("exitDate", "2026-12-31"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.employmentStatus").value("EXITED"))
				.andExpect(jsonPath("$.exitDate").value("2026-12-31"));

		await().atMost(Duration.ofSeconds(15))
				.until(() -> "DISABLED".equals(fixtures.userStatus(tenant, arun.email())));
		assertThat(identityProvider.find(arun.email())).hasValueSatisfying(user -> assertThat(user.enabled()).isFalse());
		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", arunToken))
				.andExpect(status().isForbidden());
		send(put("/api/v1/employees/{id}", arun.id()), admin, update(arun, null))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("EMPLOYEE_EXITED"));
	}

	@Test
	void employeesCannotManageEmployees() throws Exception {
		String token = tokenFor(fixtures.createEmployee(tenant, "Pat", department, null));
		send(post("/api/v1/employees"), token, employee(TestFixtures.unique("C"), "No", "no@x.test", null))
				.andExpect(status().isForbidden());
	}

	private String tokenFor(EmployeeResponse employee) {
		return fixtures.bearer(tenant, fixtures.awaitSubject(tenant, employee.email()));
	}

	private ResultActions listNames(String bearer) throws Exception {
		return mockMvc.perform(get("/api/v1/employees?size=100").header("Authorization", bearer))
				.andExpect(status().isOk());
	}

	private Map<String, Object> employee(String code, String firstName, String email, UUID managerId) {
		Map<String, Object> body = new HashMap<>();
		body.put("employeeCode", code);
		body.put("firstName", firstName);
		body.put("lastName", "Tester");
		body.put("email", email);
		body.put("departmentId", department);
		body.put("reportingManagerId", managerId);
		body.put("employmentType", "FULL_TIME");
		body.put("employmentStatus", "ACTIVE");
		body.put("joiningDate", "2024-01-01");
		return body;
	}

	private Map<String, Object> update(EmployeeResponse current, UUID managerId) {
		return employee(current.employeeCode(), current.firstName(), current.email(), managerId);
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String bearer, Object body) throws Exception {
		return mockMvc.perform(request.header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(body)));
	}

}
