package com.bsolz.lms.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.support.ApiClient;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Calendars and availability. Carla manages Maya and Nora; Maya (a manager) manages Eli; Omar is in
 * another department.
 */
@IntegrationTest
class CalendarApiTests {

	@Autowired
	ApiClient api;

	@Autowired
	TestFixtures fixtures;

	TestTenant tenant;

	UUID department;

	UUID otherDepartment;

	EmployeeResponse carla;

	EmployeeResponse maya;

	EmployeeResponse nora;

	EmployeeResponse eli;

	EmployeeResponse omar;

	String carlaToken;

	String mayaToken;

	String noraToken;

	String eliToken;

	LocalDate monday;

	@BeforeEach
	void setUp() {
		tenant = fixtures.newTenant("cal");
		department = fixtures.createDepartment(tenant);
		otherDepartment = fixtures.createDepartment(tenant);
		carla = fixtures.createEmployee(tenant, "Carla", department, null);
		maya = fixtures.createEmployee(tenant, "Maya", department, carla.id());
		nora = fixtures.createEmployee(tenant, "Nora", department, carla.id());
		eli = fixtures.createEmployee(tenant, "Eli", department, maya.id());
		omar = fixtures.createEmployee(tenant, "Omar", otherDepartment, null);
		carlaToken = tokenFor(carla);
		mayaToken = tokenFor(maya);
		noraToken = tokenFor(nora);
		eliToken = tokenFor(eli);
		tokenFor(omar);
		fixtures.grantRoles(tenant, maya.email(), "MANAGER");
		fixtures.allocateAroundToday(tenant);
		monday = ApiClient.mondayAhead(fixtures.today(tenant));
	}

	@Test
	void teamCalendarsHideLeaveTypesOfColleaguesTheViewerMayNotSee() throws Exception {
		// Nora: approved sick leave. Eli: pending annual leave.
		api.submitLeave(noraToken, fixtures.leaveTypeId(tenant, "SICK"), monday, monday);
		api.approveFirstTask(carlaToken);
		String eliRequest = api.submitLeave(eliToken, fixtures.leaveTypeId(tenant, "ANNUAL"), monday.plusDays(1),
				monday.plusDays(2)).get("id").asString();

		JsonNode mayas = calendar(mayaToken, "team");
		assertThat(names(mayas)).containsExactly("Carla Tester", "Eli Tester", "Maya Tester", "Nora Tester");
		JsonNode noraLeave = leaveOf(mayas, nora);
		assertThat(noraLeave.get("leaveType").isNull()).isTrue();
		assertThat(noraLeave.get("id").isNull()).isTrue();
		assertThat(noraLeave.get("status").asString()).isEqualTo("APPROVED");
		JsonNode eliLeave = leaveOf(mayas, eli);
		assertThat(eliLeave.get("id").asString()).isEqualTo(eliRequest);
		assertThat(eliLeave.get("leaveType").get("code").asString()).isEqualTo("ANNUAL");
		assertThat(eliLeave.get("status").asString()).isEqualTo("PENDING");

		// Nora's team: her manager and peers; she sees her own leave in detail, Eli is not in her team.
		JsonNode noras = calendar(noraToken, "team");
		assertThat(names(noras)).containsExactly("Carla Tester", "Maya Tester", "Nora Tester");
		assertThat(leaveOf(noras, nora).get("leaveType").get("code").asString()).isEqualTo("SICK");

		assertThat(names(calendar(eliToken, "me"))).containsExactly("Eli Tester");
		assertThat(calendar(eliToken, "me").get("leaves")).hasSize(1);
	}

	@Test
	void departmentCalendarsAreLimitedToTheViewersDepartmentUnlessTheyMaySeeEveryone() throws Exception {
		assertThat(names(calendar(eliToken, "department"))).containsExactly("Carla Tester", "Eli Tester",
				"Maya Tester", "Nora Tester");
		api.get(eliToken, "/api/v1/calendar?scope=department&departmentId={d}&from={f}&to={t}", otherDepartment,
				monday, monday.plusDays(6))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("DEPARTMENT_NOT_VISIBLE"));

		fixtures.grantRoles(tenant, carla.email(), "HR_ADMIN");
		api.get(carlaToken, "/api/v1/calendar?scope=department&departmentId={d}&from={f}&to={t}", otherDepartment,
				monday, monday.plusDays(6))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members[*].name", containsInAnyOrder("Omar Tester")));

		api.get(eliToken, "/api/v1/calendar?scope=everyone&from={f}&to={t}", monday, monday)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_SCOPE"));
		api.get(eliToken, "/api/v1/calendar?from={f}&to={t}", monday, monday.plusDays(92))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_RANGE"));
		api.get(fixtures.adminBearer(tenant), "/api/v1/calendar?from={f}&to={t}", monday, monday)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("NOT_LINKED_TO_EMPLOYEE"));
	}

	@Test
	void availabilityCombinesApprovedLeaveHolidaysAndWorkSchedules() throws Exception {
		// Monday: Nora on sick leave, Carla off in the morning, Eli working from home, Maya in.
		api.submitLeave(noraToken, fixtures.leaveTypeId(tenant, "SICK"), monday, monday);
		api.approveFirstTask(carlaToken);
		Map<String, Object> halfDay = new HashMap<>();
		halfDay.put("leaveTypeId", fixtures.leaveTypeId(tenant, "ANNUAL"));
		halfDay.put("startDate", monday.toString());
		halfDay.put("endDate", monday.toString());
		halfDay.put("startSession", "FIRST_HALF");
		halfDay.put("endSession", "FIRST_HALF");
		api.submitLeave(carlaToken, halfDay);
		api.submitLeave(eliToken, fixtures.leaveTypeId(tenant, "WFH"), monday, monday);
		api.approveFirstTask(mayaToken);
		// Tuesday: a company holiday.
		api.post(fixtures.adminBearer(tenant), "/api/v1/holidays",
				Map.of("name", "Founders Day", "date", monday.plusDays(1).toString(), "type", "COMPANY"))
				.andExpect(status().isCreated());

		JsonNode mondayView = availability(mayaToken, monday);
		assertThat(statusOf(mondayView, nora)).isEqualTo("ON_LEAVE");
		assertThat(entryOf(mondayView, nora).get("leaveType").isNull()).isTrue();
		assertThat(statusOf(mondayView, carla)).isEqualTo("HALF_DAY_LEAVE");
		assertThat(entryOf(mondayView, carla).get("session").asString()).isEqualTo("FIRST_HALF");
		assertThat(statusOf(mondayView, eli)).isEqualTo("REMOTE");
		assertThat(entryOf(mondayView, eli).get("leaveType").get("code").asString()).isEqualTo("WFH");
		assertThat(statusOf(mondayView, maya)).isEqualTo("AVAILABLE");
		assertThat(mondayView.get("counts").get("ON_LEAVE").asInt()).isEqualTo(1);
		assertThat(mondayView.get("counts").get("AVAILABLE").asInt()).isEqualTo(1);

		JsonNode tuesday = availability(mayaToken, monday.plusDays(1));
		assertThat(statusOf(tuesday, maya)).isEqualTo("HOLIDAY");
		assertThat(entryOf(tuesday, maya).get("holidayName").asString()).isEqualTo("Founders Day");
		assertThat(availability(mayaToken, monday.plusDays(5)).get("counts").get("NON_WORKING_DAY").asInt())
				.isEqualTo(4);
	}

	private JsonNode calendar(String token, String scope) throws Exception {
		return api.json(api.get(token, "/api/v1/calendar?scope={s}&from={f}&to={t}", scope, monday,
				monday.plusDays(6)).andExpect(status().isOk()));
	}

	private JsonNode availability(String token, LocalDate date) throws Exception {
		return api.json(api.get(token, "/api/v1/calendar/availability?date={d}&scope=team", date)
				.andExpect(status().isOk()));
	}

	private static List<String> names(JsonNode calendar) {
		return calendar.get("members").valueStream().map(member -> member.get("name").asString()).toList();
	}

	private static JsonNode leaveOf(JsonNode calendar, EmployeeResponse employee) {
		return calendar.get("leaves").valueStream()
				.filter(leave -> leave.get("employeeId").asString().equals(employee.id().toString()))
				.findFirst().orElseThrow();
	}

	private static JsonNode entryOf(JsonNode availability, EmployeeResponse employee) {
		return availability.get("employees").valueStream()
				.filter(entry -> entry.get("employeeId").asString().equals(employee.id().toString()))
				.findFirst().orElseThrow();
	}

	private static String statusOf(JsonNode availability, EmployeeResponse employee) {
		return entryOf(availability, employee).get("status").asString();
	}

	private String tokenFor(EmployeeResponse employee) {
		return fixtures.bearer(tenant, fixtures.awaitSubject(tenant, employee.email()));
	}

}
