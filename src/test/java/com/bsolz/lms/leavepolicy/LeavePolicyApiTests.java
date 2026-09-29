package com.bsolz.lms.leavepolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.leavepolicy.api.LeavePeriodInfo;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.leavepolicy.api.ResolvedPolicy;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.organization.model.enums.Gender;
import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class LeavePolicyApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	LeavePolicyApi policyApi;

	@Autowired
	OrganizationApi organizationApi;

	@Autowired
	JsonMapper jsonMapper;

	TestTenant tenant;

	String admin;

	@BeforeEach
	void setUp() {
		tenant = fixtures.newTenant("pol");
		admin = fixtures.adminBearer(tenant);
	}

	@Test
	void seedsLeaveTypesPoliciesAndTheCurrentPeriod() throws Exception {
		mockMvc.perform(get("/api/v1/leave-types").header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].code",
						contains("ANNUAL", "CASUAL", "SICK", "MATERNITY", "PATERNITY", "UNPAID", "WFH")))
				.andExpect(jsonPath("$[?(@.code == 'UNPAID')].balanceTracked").value(false))
				.andExpect(jsonPath("$[?(@.code == 'UNPAID')].paid").value(false));
		mockMvc.perform(get("/api/v1/leave-policies").header("Authorization", admin))
				.andExpect(jsonPath("$.length()").value(7))
				.andExpect(jsonPath("$[?(@.name == 'Default Maternity Leave')].appliesTo[0].gender").value("FEMALE"));

		int year = LocalDate.now().getYear();
		mockMvc.perform(get("/api/v1/leave-periods/current").header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value(String.valueOf(year)))
				.andExpect(jsonPath("$.startDate").value(year + "-01-01"))
				.andExpect(jsonPath("$.endDate").value(year + "-12-31"))
				.andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void managesLeaveTypes() throws Exception {
		String id = id(send(post("/api/v1/leave-types"), leaveType("comp_off", "Comp Off"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("COMP_OFF"))
				.andExpect(jsonPath("$.color").value("#AABBCC")));

		send(post("/api/v1/leave-types"), leaveType("COMP_OFF", "Other")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("LEAVE_TYPE_CODE_TAKEN"));
		send(post("/api/v1/leave-types"), leaveType("OTHER", "comp off")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("LEAVE_TYPE_NAME_TAKEN"));
		Map<String, Object> badColour = leaveType("BAD", "Bad");
		badColour.put("color", "red");
		send(post("/api/v1/leave-types"), badColour).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("color"));

		Map<String, Object> inactive = leaveType("COMP_OFF", "Comp Off");
		inactive.put("active", false);
		send(put("/api/v1/leave-types/{id}", id), inactive).andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
		mockMvc.perform(get("/api/v1/leave-types").header("Authorization", admin))
				.andExpect(jsonPath("$[*].code", not(hasItem("COMP_OFF"))));
		mockMvc.perform(get("/api/v1/leave-types?includeInactive=true").header("Authorization", admin))
				.andExpect(jsonPath("$[*].code", hasItem("COMP_OFF")));
	}

	@Test
	void createsLeavePeriodsWithoutOverlap() throws Exception {
		LeavePeriodInfo current = fixtures.currentPeriod(tenant);
		int next = current.startDate().getYear() + 1;

		send(post("/api/v1/leave-periods"), Map.of()).andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value(String.valueOf(next)))
				.andExpect(jsonPath("$.startDate").value(next + "-01-01"))
				.andExpect(jsonPath("$.endDate").value(next + "-12-31"));

		send(post("/api/v1/leave-periods"), Map.of("startDate", next + "-06-01", "endDate", (next + 1) + "-05-31"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("LEAVE_PERIOD_OVERLAP"));
		send(post("/api/v1/leave-periods"), Map.of("startDate", (next + 1) + "-01-05", "endDate", (next + 1) + "-12-31"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_LEAVE_PERIOD"));
		send(post("/api/v1/leave-periods"), Map.of("startDate", (next + 1) + "-01-01", "endDate", (next + 2) + "-03-31"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_LEAVE_PERIOD"));
		// A short transition period is fine, e.g. before moving to an April leave year.
		send(post("/api/v1/leave-periods"), Map.of("startDate", (next + 1) + "-01-01", "endDate", (next + 1) + "-03-31"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value(String.valueOf(next + 1)));
	}

	@Test
	void resolvesTheMostSpecificPolicy() throws Exception {
		UUID sales = fixtures.createDepartment(tenant);
		UUID pune = location("PNQ");
		UUID annual = fixtures.leaveTypeId(tenant, "ANNUAL");
		EmployeeResponse inPune = fixtures.createEmployee(tenant, "Priya", sales, null, Gender.FEMALE, pune,
				LocalDate.of(2024, 1, 1));
		EmployeeResponse elsewhere = fixtures.createEmployee(tenant, "Sam", fixtures.createDepartment(tenant), null,
				Gender.MALE, null, LocalDate.of(2024, 1, 1));

		send(post("/api/v1/leave-policies"), policy(annual, "Pune annual", 20, List.of(Map.of("locationId", pune))))
				.andExpect(status().isCreated());
		send(post("/api/v1/leave-policies"), policy(annual, "Sales annual", 22, List.of(Map.of("departmentId", sales))))
				.andExpect(status().isCreated());

		// department (16) beats location (4) beats the default's catch-all rule (0)
		assertThat(resolve(inPune.id(), annual)).map(ResolvedPolicy::policyName).hasValue("Sales annual");
		assertThat(resolve(elsewhere.id(), annual)).map(ResolvedPolicy::policyName).hasValue("Default Annual Leave");

		// Maternity only resolves for female employees, paternity only for male ones.
		UUID maternity = fixtures.leaveTypeId(tenant, "MATERNITY");
		assertThat(resolve(inPune.id(), maternity)).isPresent();
		assertThat(resolve(elsewhere.id(), maternity)).isEmpty();
		assertThat(resolve(elsewhere.id(), fixtures.leaveTypeId(tenant, "PATERNITY"))).isPresent();

		// The same through the API, for an employee the caller can see.
		mockMvc.perform(get("/api/v1/leave-policies/effective?employeeId={id}&leaveTypeId={type}", inPune.id(), annual)
				.header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].eligible").value(true))
				.andExpect(jsonPath("$[0].policy.policyName").value("Sales annual"))
				.andExpect(jsonPath("$[0].policy.entitlementDays").value(22.0));
		String samToken = fixtures.bearer(tenant, fixtures.awaitSubject(tenant, elsewhere.email()));
		mockMvc.perform(get("/api/v1/leave-policies/effective").header("Authorization", samToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.leaveTypeCode == 'MATERNITY')].eligible").value(false));
		mockMvc.perform(get("/api/v1/leave-policies/effective?employeeId={id}", inPune.id())
				.header("Authorization", samToken))
				.andExpect(status().isForbidden());
	}

	@Test
	void effectiveDatesDecideWhichPolicyApplies() throws Exception {
		UUID sick = fixtures.leaveTypeId(tenant, "SICK");
		UUID department = fixtures.createDepartment(tenant);
		EmployeeResponse employee = fixtures.createEmployee(tenant, "Eve", department, null);
		int year = fixtures.currentPeriod(tenant).startDate().getYear();

		Map<String, Object> body = policy(sick, "Sick from July", 14, List.of(Map.of("departmentId", department)));
		body.put("effectiveFrom", year + "-07-01");
		send(post("/api/v1/leave-policies"), body).andExpect(status().isCreated());

		assertThat(resolve(employee.id(), sick, LocalDate.of(year, 6, 30))).map(ResolvedPolicy::policyName)
				.hasValue("Default Sick Leave");
		assertThat(resolve(employee.id(), sick, LocalDate.of(year, 7, 1))).map(ResolvedPolicy::policyName)
				.hasValue("Sick from July");
	}

	@Test
	void rejectsConflictingAndInvalidPolicies() throws Exception {
		UUID casual = fixtures.leaveTypeId(tenant, "CASUAL");
		// The seeded default already applies to everyone for all time.
		send(post("/api/v1/leave-policies"), policy(casual, "Another default", 5, List.of()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("POLICY_CONFLICT"));
		Map<String, Object> inactive = policy(casual, "Draft default", 5, List.of());
		inactive.put("active", false);
		send(post("/api/v1/leave-policies"), inactive).andExpect(status().isCreated());

		send(post("/api/v1/leave-policies"), policy(casual, "Unknown department", 5,
				List.of(Map.of("departmentId", UUID.randomUUID()))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("UNKNOWN_ORG_UNIT"));
		Map<String, Object> quarterDays = policy(casual, "Quarter days", 5, List.of());
		quarterDays.put("entitlementDays", 5.25);
		send(post("/api/v1/leave-policies"), quarterDays).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("entitlementDays"));
		Map<String, Object> backwards = policy(casual, "Backwards", 5, List.of(Map.of("gender", "OTHER")));
		backwards.put("effectiveFrom", "2030-01-01");
		backwards.put("effectiveTo", "2029-01-01");
		send(post("/api/v1/leave-policies"), backwards).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_EFFECTIVE_DATES"));

		String sickDefault = policyId("Default Sick Leave");
		send(put("/api/v1/leave-policies/{id}", sickDefault), policy(casual, "Default Sick Leave", 10, List.of()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("POLICY_LEAVE_TYPE_IMMUTABLE"));
	}

	@Test
	void employeesReadButDontChangeLeaveRules() throws Exception {
		String email = fixtures.createEmployee(tenant, "Reader", fixtures.createDepartment(tenant), null).email();
		String employee = fixtures.bearer(tenant, fixtures.awaitSubject(tenant, email));

		mockMvc.perform(get("/api/v1/leave-types").header("Authorization", employee)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/leave-policies").header("Authorization", employee)).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/leave-types").header("Authorization", employee)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(leaveType("NOPE", "Nope"))))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/leave-periods").header("Authorization", employee)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
	}

	private Optional<ResolvedPolicy> resolve(UUID employeeId, UUID leaveTypeId) {
		return resolve(employeeId, leaveTypeId, LocalDate.now());
	}

	private Optional<ResolvedPolicy> resolve(UUID employeeId, UUID leaveTypeId, LocalDate asOf) {
		return TenantContext.call(tenant.info(), () -> policyApi
				.resolve(organizationApi.findEmployee(employeeId).orElseThrow(), leaveTypeId, asOf));
	}

	private UUID location(String code) throws Exception {
		return UUID.fromString(id(send(post("/api/v1/locations"), Map.of("code", code + TestFixtures.unique("").substring(1, 5),
				"name", code, "countryCode", "IN", "timezone", "Asia/Kolkata"))
				.andExpect(status().isCreated())));
	}

	private String policyId(String name) throws Exception {
		return jsonMapper.readTree(mockMvc.perform(get("/api/v1/leave-policies").header("Authorization", admin))
				.andReturn().getResponse().getContentAsString()).valueStream()
				.filter(node -> node.get("name").asString().equals(name))
				.findFirst().orElseThrow().get("id").asString();
	}

	private static Map<String, Object> leaveType(String code, String name) {
		Map<String, Object> body = new HashMap<>();
		body.put("code", code);
		body.put("name", name);
		body.put("color", "#aabbcc");
		body.put("paid", true);
		body.put("balanceTracked", true);
		return body;
	}

	private static Map<String, Object> policy(UUID leaveTypeId, String name, int days, List<Map<String, Object>> appliesTo) {
		Map<String, Object> body = new HashMap<>();
		body.put("leaveTypeId", leaveTypeId);
		body.put("name", name);
		body.put("entitlementDays", days);
		body.put("accrualMethod", "UPFRONT");
		body.put("prorateOnJoining", true);
		body.put("appliesTo", appliesTo);
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
