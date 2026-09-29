package com.bsolz.lms.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.support.ApiClient;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** The audit trail and the dashboard's activity feed. Carla manages Maya, who manages Eli; Omar is elsewhere. */
@IntegrationTest
class AuditApiTests {

	@Autowired
	ApiClient api;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	JdbcTemplate jdbcTemplate;

	TestTenant tenant;

	String admin;

	EmployeeResponse carla;

	EmployeeResponse maya;

	EmployeeResponse eli;

	EmployeeResponse omar;

	@BeforeEach
	void setUp() {
		tenant = fixtures.newTenant("aud");
		admin = fixtures.adminBearer(tenant);
		UUID department = fixtures.createDepartment(tenant);
		carla = fixtures.createEmployee(tenant, "Carla", department, null);
		maya = fixtures.createEmployee(tenant, "Maya", department, carla.id());
		eli = fixtures.createEmployee(tenant, "Eli", department, maya.id());
		omar = fixtures.createEmployee(tenant, "Omar", fixtures.createDepartment(tenant), null);
		fixtures.allocateAroundToday(tenant);
	}

	@Test
	void recordsEachStepOfALeaveRequest() throws Exception {
		String eliToken = tokenFor(eli);
		String mayaToken = tokenFor(maya);
		String carlaToken = tokenFor(carla);
		fixtures.grantRoles(tenant, carla.email(), "HR_ADMIN");
		LocalDate monday = ApiClient.mondayAhead(fixtures.today(tenant));
		String requestId = api.submitLeave(eliToken, fixtures.leaveTypeId(tenant, "ANNUAL"), monday,
				monday.plusDays(4)).get("id").asString();
		api.approveFirstTask(mayaToken);
		api.approveFirstTask(carlaToken);

		JsonNode entries = awaitEntries("entityId=" + requestId, 3);
		String dates = "(" + monday + " to " + monday.plusDays(4) + ", 5 day(s))";
		assertThat(entries.get(2).get("action").asString()).isEqualTo("LEAVE_SUBMITTED");
		assertThat(entries.get(2).get("summary").asString()).isEqualTo("Eli Tester requested Annual Leave " + dates);
		assertThat(entries.get(1).get("action").asString()).isEqualTo("APPROVAL_STEP_APPROVED");
		assertThat(entries.get(1).get("summary").asString())
				.isEqualTo("Maya Tester approved step 1 of Eli Tester's Annual Leave " + dates);
		assertThat(entries.get(0).get("action").asString()).isEqualTo("LEAVE_APPROVED");
		assertThat(entries.get(0).get("summary").asString())
				.isEqualTo("Carla Tester approved Eli Tester's Annual Leave " + dates);
		assertThat(entries.get(0).get("actor").get("name").asString()).isEqualTo("Carla Tester");
		assertThat(entries.get(0).get("employee").get("id").asString()).isEqualTo(eli.id().toString());
		assertThat(entries.get(0).get("details").get("from").asString()).isEqualTo("PENDING");
		assertThat(entries.get(0).get("details").get("to").asString()).isEqualTo("APPROVED");

		api.get(eliToken, "/api/v1/audit-logs").andExpect(status().isForbidden());
	}

	@Test
	void recordsWhoAddedAnEmployee() throws Exception {
		String email = TestFixtures.unique("nina") + "@example.test";
		Map<String, Object> nina = new HashMap<>();
		nina.put("employeeCode", TestFixtures.unique("E"));
		nina.put("firstName", "Nina");
		nina.put("lastName", "Tester");
		nina.put("email", email);
		nina.put("departmentId", omar.department().id());
		nina.put("employmentType", "FULL_TIME");
		nina.put("employmentStatus", "ACTIVE");
		nina.put("joiningDate", "2024-01-01");
		String ninaId = api.json(api.post(admin, "/api/v1/employees", nina).andExpect(status().isCreated()))
				.get("id").asString();

		JsonNode entry = awaitEntries("action=EMPLOYEE_CREATED&employeeId=" + ninaId, 1).get(0);
		assertThat(entry.get("summary").asString()).isEqualTo(tenant.adminEmail() + " added Nina Tester (" + email + ")");
		assertThat(entry.get("actor").get("name").asString()).isEqualTo(tenant.adminEmail());
		// Employees created by the system (here: the test fixtures, outside any request) have no actor.
		JsonNode fixtureEntry = awaitEntries("action=EMPLOYEE_CREATED&employeeId=" + eli.id(), 1).get(0);
		assertThat(fixtureEntry.get("actor").isNull()).isTrue();
		assertThat(fixtureEntry.get("summary").asString()).startsWith("The system added Eli Tester");
	}

	@Test
	void activityFeedsShowOnlyWhatTheViewerMaySee() throws Exception {
		String eliToken = tokenFor(eli);
		String mayaToken = tokenFor(maya);
		String omarToken = tokenFor(omar);
		fixtures.grantRoles(tenant, maya.email(), "MANAGER");
		LocalDate monday = ApiClient.mondayAhead(fixtures.today(tenant));
		String requestId = api.submitLeave(eliToken, fixtures.leaveTypeId(tenant, "ANNUAL"), monday, monday)
				.get("id").asString();
		awaitEntries("entityId=" + requestId, 1);

		api.get(eliToken, "/api/v1/dashboard/activities")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].entityId", hasItem(requestId)))
				.andExpect(jsonPath("$[0].details").doesNotExist());
		api.get(mayaToken, "/api/v1/dashboard/activities").andExpect(jsonPath("$[*].entityId", hasItem(requestId)));
		api.get(omarToken, "/api/v1/dashboard/activities")
				.andExpect(jsonPath("$[*].entityId", not(hasItem(requestId))));
		api.get(admin, "/api/v1/dashboard/activities?limit=500").andExpect(jsonPath("$[*].entityId", hasItem(requestId)));
	}

	@Test
	void balanceAdjustmentsAreAuditedAndTheLogIsAppendOnly() throws Exception {
		api.post(admin, "/api/v1/balances/adjustments", Map.of("employeeId", eli.id(), "leaveTypeId",
				fixtures.leaveTypeId(tenant, "ANNUAL"), "amount", 1.5, "reason", "Worked on a holiday"))
				.andExpect(status().isOk());

		JsonNode entry = awaitEntries("action=BALANCE_ADJUSTED", 1).get(0);
		assertThat(entry.get("summary").asString())
				.isEqualTo(tenant.adminEmail() + " adjusted Eli Tester's Annual Leave balance by +1.5 day(s)");
		assertThat(entry.get("details").get("reason").asString()).isEqualTo("Worked on a holiday");

		assertThatThrownBy(() -> TenantContext.run(tenant.info(),
				() -> jdbcTemplate.update("UPDATE activity_log SET summary = 'changed'")))
				.hasMessageContaining("activity_log is append-only");
		assertThatThrownBy(() -> TenantContext.run(tenant.info(), () -> jdbcTemplate.update("DELETE FROM activity_log")))
				.hasMessageContaining("activity_log is append-only");
	}

	private JsonNode awaitEntries(String query, int count) throws Exception {
		await().atMost(Duration.ofSeconds(15)).until(() -> api.json(api.get(admin, "/api/v1/audit-logs?" + query))
				.get("totalElements").asInt() >= count);
		return api.json(api.get(admin, "/api/v1/audit-logs?" + query)).get("content");
	}

	private String tokenFor(EmployeeResponse employee) {
		return fixtures.bearer(tenant, fixtures.awaitSubject(tenant, employee.email()));
	}

}
