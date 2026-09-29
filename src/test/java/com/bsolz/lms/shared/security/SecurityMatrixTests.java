package com.bsolz.lms.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every endpoint × every system role: a role without the endpoint's permission gets 403 ACCESS_DENIED;
 * a role with it gets anything else (the ids are random, so usually 404 - authorization passed). Bodies
 * are valid, because they are validated before authorization is checked. {@link #everyEndpointIsInTheMatrix}
 * fails when an endpoint is added without a row here.
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityMatrixTests {

	/** The seeded system roles' permissions (004-seed-roles-permissions.sql); every user also has EMPLOYEE. */
	private static final Map<String, Set<String>> ROLES = new LinkedHashMap<>();

	static {
		Set<String> employee = Set.of(Permissions.LEAVE_APPLY);
		Set<String> manager = Set.of(Permissions.LEAVE_APPLY, Permissions.LEAVE_APPROVE, Permissions.EMPLOYEE_VIEW_TEAM,
				Permissions.REPORT_VIEW_TEAM);
		Set<String> hr = new TreeSet<>(Permissions.ALL);
		hr.removeAll(Set.of(Permissions.ROLE_MANAGE, Permissions.SETTINGS_MANAGE));
		ROLES.put(SystemRoles.EMPLOYEE, employee);
		ROLES.put(SystemRoles.MANAGER, manager);
		ROLES.put(SystemRoles.HR_ADMIN, hr);
		ROLES.put(SystemRoles.TENANT_ADMIN, Set.copyOf(Permissions.ALL));
	}

	private static final String ID = "/00000000-0000-4000-8000-000000000001";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	JsonMapper jsonMapper;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	final Map<String, String> tokens = new LinkedHashMap<>();

	@BeforeAll
	void setUp() {
		TestTenant tenant = fixtures.newTenant("matrix");
		UUID department = fixtures.createDepartment(tenant);
		for (String role : ROLES.keySet()) {
			EmployeeResponse employee = fixtures.createEmployee(tenant, role.charAt(0) + role.substring(1).toLowerCase(),
					department, null);
			String subject = fixtures.awaitSubject(tenant, employee.email());
			if (!role.equals(SystemRoles.EMPLOYEE)) {
				fixtures.grantRoles(tenant, employee.email(), role);
			}
			tokens.put(role, fixtures.bearer(tenant, subject));
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("endpoints")
	void tenantEndpointsEnforceTheirPermissions(Endpoint endpoint) throws Exception {
		List<String> mismatches = new ArrayList<>();
		for (Map.Entry<String, Set<String>> role : ROLES.entrySet()) {
			boolean allowed = endpoint.allowed().test(role.getValue());
			MockHttpServletResponse response = perform(endpoint, tokens.get(role.getKey()));
			boolean denied = response.getStatus() == 403 && response.getContentAsString().contains("\"ACCESS_DENIED\"");
			if (allowed == denied) {
				mismatches.add(role.getKey() + " expected " + (allowed ? "access" : "ACCESS_DENIED") + " but got "
						+ response.getStatus() + " " + response.getContentAsString());
			}
		}
		assertThat(perform(endpoint, null).getStatus()).as("without a token").isEqualTo(401);
		assertThat(mismatches).isEmpty();
	}

	@Test
	void platformEndpointsRejectTenantTokens() throws Exception {
		for (String path : List.of("/platform/tenants", "/platform/tenants" + ID)) {
			for (String token : tokens.values()) {
				assertThat(mockMvc.perform(request(HttpMethod.GET, path).header("Authorization", token))
						.andReturn().getResponse().getStatus()).as(path).isEqualTo(401);
			}
			assertThat(mockMvc.perform(request(HttpMethod.GET, path)
					.header("Authorization", fixtures.platformAdminBearer())).andReturn().getResponse().getStatus())
					.as(path).isIn(200, 404);
		}
	}

	@Test
	void everyEndpointIsInTheMatrix() {
		Set<String> covered = new TreeSet<>();
		endpoints().forEach(endpoint -> covered.add(endpoint.method() + " " + endpoint.path().replaceAll("\\?.*$", "")
				.replaceAll("/[0-9a-f-]{36}", "/{}")));
		Set<String> mapped = new TreeSet<>();
		handlerMapping.getHandlerMethods().keySet().forEach(info -> info.getPathPatternsCondition().getPatternValues()
				.stream()
				.filter(pattern -> pattern.startsWith("/api/") && !pattern.startsWith("/api/test/"))
				.forEach(pattern -> info.getMethodsCondition().getMethods().forEach(
						method -> mapped.add(method + " " + pattern.replaceAll("\\{[^}]+}", "{}")))));
		assertThat(covered).containsAll(mapped);
	}

	private MockHttpServletResponse perform(Endpoint endpoint, String token) throws Exception {
		HttpHeaders headers = new HttpHeaders();
		if (token != null) {
			headers.set("Authorization", token);
		}
		RequestBuilder request = endpoint.path().endsWith("/holidays/import")
				? multipart(endpoint.path()).file(new MockMultipartFile("file", "holidays.csv", "text/csv",
						"name,date\nNew Year,2030-01-01\n".getBytes())).headers(headers)
				: request(HttpMethod.valueOf(endpoint.method()), endpoint.path()).headers(headers)
						.contentType(MediaType.APPLICATION_JSON)
						.content(jsonMapper.writeValueAsString(endpoint.body() == null ? Map.of() : endpoint.body()));
		return mockMvc.perform(request).andReturn().getResponse();
	}

	static Stream<Endpoint> endpoints() {
		Map<String, Object> workflow = Map.of("name", "Matrix", "priority", 77, "rules", List.of(Map.of("minDays", 3)),
				"steps", List.of(Map.of("approverType", "REPORTING_MANAGER")));
		Map<String, Object> leave = Map.of("leaveTypeId", UUID.randomUUID(), "startDate", "2030-01-07", "endDate",
				"2030-01-07");
		Map<String, Object> employee = Map.of("employeeCode", "MX-1", "firstName", "M", "lastName", "X", "email",
				"mx@example.test", "departmentId", UUID.randomUUID(), "employmentType", "FULL_TIME", "employmentStatus",
				"ACTIVE", "joiningDate", "2024-01-01");
		Map<String, Object> policy = Map.of("leaveTypeId", UUID.randomUUID(), "name", "Matrix", "entitlementDays", 1,
				"accrualMethod", "UPFRONT", "prorateOnJoining", true);
		Map<String, Object> leaveType = Map.of("code", "MATRIX", "name", "Matrix", "color", "#112233", "paid", true,
				"balanceTracked", false);
		Map<String, Object> location = Map.of("code", "MX", "name", "Matrix", "countryCode", "IN", "timezone",
				"Asia/Kolkata");
		Map<String, Object> schedule = Map.of("name", "Matrix", "workingDays", List.of("MONDAY"), "defaultSchedule", false);
		Map<String, Object> role = Map.of("code", "MATRIX_ROLE", "name", "Matrix", "permissionCodes", List.of("LEAVE_APPLY"));
		Map<String, Object> settings = Map.of("leaveYearStartMonth", 1, "dateFormat", "dd/MM/yyyy", "weekStartDay",
				"MONDAY");

		return Stream.of(
				// Anyone signed in
				any("GET", "/api/v1/auth/me"),
				any("GET", "/api/v1/settings"),
				any("GET", "/api/v1/settings/date-formats"),
				any("GET", "/api/v1/departments"), any("GET", "/api/v1/departments" + ID),
				any("GET", "/api/v1/designations"), any("GET", "/api/v1/designations" + ID),
				any("GET", "/api/v1/locations"), any("GET", "/api/v1/locations" + ID),
				any("GET", "/api/v1/work-schedules"), any("GET", "/api/v1/work-schedules" + ID),
				any("GET", "/api/v1/employees"), any("GET", "/api/v1/employees/me"),
				any("GET", "/api/v1/employees/me/team"),
				any("GET", "/api/v1/leave-types"), any("GET", "/api/v1/leave-types" + ID),
				any("GET", "/api/v1/leave-policies"), any("GET", "/api/v1/leave-policies" + ID),
				any("GET", "/api/v1/leave-policies/effective"),
				any("GET", "/api/v1/leave-periods"), any("GET", "/api/v1/leave-periods" + ID),
				any("GET", "/api/v1/leave-periods/current"),
				any("GET", "/api/v1/holidays"), any("GET", "/api/v1/holidays" + ID), any("GET", "/api/v1/holidays/me"),
				any("GET", "/api/v1/balances"), any("GET", "/api/v1/balances" + ID + "/transactions"),
				any("GET", "/api/v1/leave-requests"), any("GET", "/api/v1/leave-requests" + ID),
				any("GET", "/api/v1/leave-requests/pending-approval"),
				any("POST", "/api/v1/leave-requests" + ID + "/withdraw"),
				any("POST", "/api/v1/leave-requests" + ID + "/cancel", Map.of("reason", "Matrix")),
				any("GET", "/api/v1/leave-requests" + ID + "/attachments" + ID + "/download"),
				any("GET", "/api/v1/approvals/tasks"),
				any("POST", "/api/v1/approvals/tasks" + ID + "/approve", Map.of("comment", "ok")),
				any("POST", "/api/v1/approvals/tasks" + ID + "/reject", Map.of("comment", "no")),
				any("GET", "/api/v1/notifications"), any("GET", "/api/v1/notifications/unread-count"),
				any("POST", "/api/v1/notifications" + ID + "/read"), any("POST", "/api/v1/notifications/read-all"),
				any("GET", "/api/v1/notifications/stream"),
				any("GET", "/api/v1/calendar?from=2030-01-01&to=2030-01-31"),
				any("GET", "/api/v1/calendar/availability?date=2030-01-07"),
				any("GET", "/api/v1/dashboard/summary"), any("GET", "/api/v1/dashboard/activities"),
				// Applying for leave
				perm("POST", "/api/v1/leave-requests", leave, Permissions.LEAVE_APPLY),
				perm("POST", "/api/v1/leave-requests/preview", leave, Permissions.LEAVE_APPLY),
				perm("POST", "/api/v1/leave-requests/attachments",
						Map.of("fileName", "a.pdf", "contentType", "application/pdf", "sizeBytes", 10),
						Permissions.LEAVE_APPLY),
				// Employees: another employee's record needs a view scope that covers them
				perm("GET", "/api/v1/employees" + ID, null, Permissions.EMPLOYEE_VIEW_ALL),
				perm("POST", "/api/v1/employees", employee, Permissions.EMPLOYEE_MANAGE),
				perm("PUT", "/api/v1/employees" + ID, employee, Permissions.EMPLOYEE_MANAGE),
				perm("POST", "/api/v1/employees" + ID + "/exit", Map.of("exitDate", "2030-01-01"),
						Permissions.EMPLOYEE_MANAGE),
				// Organisation structure
				perm("POST", "/api/v1/departments", Map.of("code", "MX", "name", "Matrix"), Permissions.ORGANIZATION_MANAGE),
				perm("PUT", "/api/v1/departments" + ID, Map.of("code", "MX", "name", "Matrix"),
						Permissions.ORGANIZATION_MANAGE),
				perm("POST", "/api/v1/designations", Map.of("name", "Matrix"), Permissions.ORGANIZATION_MANAGE),
				perm("PUT", "/api/v1/designations" + ID, Map.of("name", "Matrix"), Permissions.ORGANIZATION_MANAGE),
				perm("POST", "/api/v1/locations", location, Permissions.ORGANIZATION_MANAGE),
				perm("PUT", "/api/v1/locations" + ID, location, Permissions.ORGANIZATION_MANAGE),
				perm("POST", "/api/v1/work-schedules", schedule, Permissions.ORGANIZATION_MANAGE),
				perm("PUT", "/api/v1/work-schedules" + ID, schedule, Permissions.ORGANIZATION_MANAGE),
				// Users and roles
				perm("GET", "/api/v1/users", null, Permissions.USER_MANAGE),
				perm("GET", "/api/v1/users" + ID, null, Permissions.USER_MANAGE),
				perm("POST", "/api/v1/users", Map.of("email", "matrix@example.test", "roleCodes", List.of("EMPLOYEE")),
						Permissions.USER_MANAGE),
				perm("PUT", "/api/v1/users" + ID + "/roles", Map.of("roleCodes", List.of("EMPLOYEE")),
						Permissions.USER_MANAGE),
				perm("POST", "/api/v1/users" + ID + "/disable", null, Permissions.USER_MANAGE),
				perm("POST", "/api/v1/users" + ID + "/enable", null, Permissions.USER_MANAGE),
				perm("GET", "/api/v1/roles", null, Permissions.USER_MANAGE, Permissions.ROLE_MANAGE),
				perm("GET", "/api/v1/roles" + ID, null, Permissions.USER_MANAGE, Permissions.ROLE_MANAGE),
				perm("GET", "/api/v1/permissions", null, Permissions.USER_MANAGE, Permissions.ROLE_MANAGE),
				perm("POST", "/api/v1/roles", role, Permissions.ROLE_MANAGE),
				perm("PUT", "/api/v1/roles" + ID, Map.of("name", "Matrix", "permissionCodes", List.of("LEAVE_APPLY")),
						Permissions.ROLE_MANAGE),
				perm("DELETE", "/api/v1/roles" + ID, null, Permissions.ROLE_MANAGE),
				// Settings
				perm("PUT", "/api/v1/settings", settings, Permissions.SETTINGS_MANAGE),
				// Leave policy
				perm("POST", "/api/v1/leave-types", leaveType, Permissions.LEAVE_POLICY_MANAGE),
				perm("PUT", "/api/v1/leave-types" + ID, leaveType, Permissions.LEAVE_POLICY_MANAGE),
				perm("POST", "/api/v1/leave-policies", policy, Permissions.LEAVE_POLICY_MANAGE),
				perm("PUT", "/api/v1/leave-policies" + ID, policy, Permissions.LEAVE_POLICY_MANAGE),
				perm("POST", "/api/v1/leave-periods", Map.of("startDate", "2031-01-01", "endDate", "2031-12-31"),
						Permissions.LEAVE_POLICY_MANAGE),
				// Holidays
				perm("POST", "/api/v1/holidays", Map.of("name", "Matrix", "date", "2030-01-01"), Permissions.HOLIDAY_MANAGE),
				perm("PUT", "/api/v1/holidays" + ID, Map.of("name", "Matrix", "date", "2030-01-01"),
						Permissions.HOLIDAY_MANAGE),
				perm("DELETE", "/api/v1/holidays" + ID, null, Permissions.HOLIDAY_MANAGE),
				perm("POST", "/api/v1/holidays/import", null, Permissions.HOLIDAY_MANAGE),
				// Balances
				perm("POST", "/api/v1/balances/adjustments", Map.of("employeeId", UUID.randomUUID(), "leaveTypeId",
						UUID.randomUUID(), "amount", 1, "reason", "Matrix"), Permissions.BALANCE_ADJUST),
				perm("POST", "/api/v1/balances/allocations", null, Permissions.BALANCE_ADJUST),
				// Approval workflows
				perm("GET", "/api/v1/approval-workflows", null, Permissions.WORKFLOW_MANAGE),
				perm("GET", "/api/v1/approval-workflows" + ID, null, Permissions.WORKFLOW_MANAGE),
				perm("POST", "/api/v1/approval-workflows", workflow, Permissions.WORKFLOW_MANAGE),
				perm("PUT", "/api/v1/approval-workflows" + ID, workflow, Permissions.WORKFLOW_MANAGE),
				// Audit
				perm("GET", "/api/v1/audit-logs", null, Permissions.AUDIT_VIEW),
				// Reports
				perm("GET", "/api/v1/reports/monthly-statistics", null, Permissions.REPORT_VIEW_TEAM,
						Permissions.REPORT_VIEW_ALL),
				perm("GET", "/api/v1/reports/employee-leave-history", null, Permissions.REPORT_VIEW_TEAM,
						Permissions.REPORT_VIEW_ALL),
				perm("GET", "/api/v1/reports/department-usage", null, Permissions.REPORT_VIEW_TEAM,
						Permissions.REPORT_VIEW_ALL),
				perm("GET", "/api/v1/reports/leave-type-usage", null, Permissions.REPORT_VIEW_TEAM,
						Permissions.REPORT_VIEW_ALL),
				perm("GET", "/api/v1/reports/decisions", null, Permissions.REPORT_VIEW_TEAM, Permissions.REPORT_VIEW_ALL),
				perm("GET", "/api/v1/reports/balances", null, Permissions.REPORT_VIEW_TEAM, Permissions.REPORT_VIEW_ALL),
				exports("POST", "/api/v1/reports/exports", Map.of("report", "LEAVE_TYPE_USAGE", "format", "CSV")),
				exports("GET", "/api/v1/reports/exports", null),
				exports("GET", "/api/v1/reports/exports" + ID, null));
	}

	private static Endpoint any(String method, String path) {
		return any(method, path, null);
	}

	private static Endpoint any(String method, String path, Object body) {
		return new Endpoint(method, path, body, "anyone", permissions -> true);
	}

	/** Allowed with any of the permissions. */
	private static Endpoint perm(String method, String path, Object body, String... anyOf) {
		return new Endpoint(method, path, body, String.join(" or ", anyOf),
				permissions -> Stream.of(anyOf).anyMatch(permissions::contains));
	}

	private static Endpoint exports(String method, String path, Object body) {
		Predicate<Set<String>> allowed = permissions -> permissions.contains(Permissions.REPORT_EXPORT)
				&& (permissions.contains(Permissions.REPORT_VIEW_TEAM) || permissions.contains(Permissions.REPORT_VIEW_ALL));
		return new Endpoint(method, path, body, "REPORT_EXPORT and a report view", allowed);
	}

	record Endpoint(String method, String path, Object body, String rule, Predicate<Set<String>> allowed) {

		@Override
		public String toString() {
			return method + " " + path + " (" + rule + ")";
		}

	}

}
