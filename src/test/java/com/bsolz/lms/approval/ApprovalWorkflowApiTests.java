package com.bsolz.lms.approval;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Approval workflow administration, and how workflows drive approvals. Carla manages Maya, who manages Eli. */
@IntegrationTest
class ApprovalWorkflowApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	JsonMapper jsonMapper;

	TestTenant tenant;

	String admin;

	EmployeeResponse carla;

	EmployeeResponse maya;

	EmployeeResponse eli;

	@BeforeEach
	void setUp() {
		tenant = fixtures.newTenant("wf");
		admin = fixtures.adminBearer(tenant);
		UUID department = fixtures.createDepartment(tenant);
		carla = fixtures.createEmployee(tenant, "Carla", department, null);
		maya = fixtures.createEmployee(tenant, "Maya", department, carla.id());
		eli = fixtures.createEmployee(tenant, "Eli", department, maya.id());
	}

	@Test
	void seedsTheStandardAndLongLeaveWorkflows() throws Exception {
		mockMvc.perform(get("/api/v1/approval-workflows").header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].name", contains("Long leave", "Standard")))
				.andExpect(jsonPath("$[0].rules[0].minDays").value(5.0))
				.andExpect(jsonPath("$[0].steps[*].approverType", contains("REPORTING_MANAGER", "ROLE")))
				.andExpect(jsonPath("$[0].steps[1].roleCode").value("HR_ADMIN"))
				.andExpect(jsonPath("$[1].defaultWorkflow").value(true))
				.andExpect(jsonPath("$[1].rules.length()").value(0));

		mockMvc.perform(get("/api/v1/approval-workflows").header("Authorization", tokenFor(eli)))
				.andExpect(status().isForbidden());
	}

	@Test
	void customWorkflowsPickTheirApprovers() throws Exception {
		EmployeeResponse nora = fixtures.createEmployee(tenant, "Nora", fixtures.createDepartment(tenant), null);
		UUID sick = fixtures.leaveTypeId(tenant, "SICK");
		send(post("/api/v1/approval-workflows"), workflow("Sick leave", 5, List.of(Map.of("leaveTypeId", sick)),
				List.of(Map.of("approverType", "SKIP_LEVEL_MANAGER"),
						Map.of("approverType", "EMPLOYEE", "employeeId", nora.id()))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.steps[1].employeeId").value(nora.id().toString()));
		fixtures.allocateAroundToday(tenant);
		String carlaToken = tokenFor(carla);
		String noraToken = tokenFor(nora);

		LocalDate monday = nextMonday();
		mockMvc.perform(post("/api/v1/leave-requests").header("Authorization", tokenFor(eli))
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(Map.of("leaveTypeId", sick, "startDate", monday.toString(),
						"endDate", monday.toString()))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.approvals[0].workflowName").value("Sick leave"))
				.andExpect(jsonPath("$.approvals[0].steps[0].approverDescription").value("Skip-level manager"))
				.andExpect(jsonPath("$.approvals[0].steps[0].assignees[0].name").value(carla.fullName()))
				.andExpect(jsonPath("$.approvals[0].steps[1].approverDescription").value(nora.fullName()));

		approveFirstTask(carlaToken).andExpect(jsonPath("$.status").value("PENDING"));
		approveFirstTask(noraToken).andExpect(jsonPath("$.status").value("APPROVED"));
	}

	@Test
	void runningApprovalsKeepTheStepsTheyStartedWith() throws Exception {
		EmployeeResponse hana = fixtures.createEmployee(tenant, "Hana", fixtures.createDepartment(tenant), null);
		String hanaToken = tokenFor(hana);
		fixtures.grantRoles(tenant, hana.email(), "HR_ADMIN");
		fixtures.allocateAroundToday(tenant);
		String mayaToken = tokenFor(maya);

		LocalDate monday = nextMonday();
		mockMvc.perform(post("/api/v1/leave-requests").header("Authorization", tokenFor(eli))
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(Map.of("leaveTypeId", fixtures.leaveTypeId(tenant, "ANNUAL"),
						"startDate", monday.toString(), "endDate", monday.plusDays(4).toString()))))
				.andExpect(jsonPath("$.approvals[0].workflowName").value("Long leave"));

		// Drop the HR step from "Long leave": the running approval still needs HR.
		JsonNode longLeave = getJson("/api/v1/approval-workflows").get(0);
		send(put("/api/v1/approval-workflows/{id}", longLeave.get("id").asString()), workflow("Long leave", 10,
				List.of(Map.of("minDays", 5)), List.of(Map.of("approverType", "REPORTING_MANAGER"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.steps.length()").value(1));

		approveFirstTask(mayaToken).andExpect(jsonPath("$.status").value("PENDING"));
		approveFirstTask(hanaToken).andExpect(jsonPath("$.status").value("APPROVED"));
	}

	@Test
	void validatesWorkflows() throws Exception {
		List<Map<String, Object>> managerStep = List.of(Map.of("approverType", "REPORTING_MANAGER"));
		List<Map<String, Object>> tenDays = List.of(Map.of("minDays", 10));

		send(post("/api/v1/approval-workflows"), workflow("No rules", 20, List.of(), managerStep))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_WORKFLOW"));
		send(post("/api/v1/approval-workflows"), workflow("Role without code", 20, tenDays,
				List.of(Map.of("approverType", "ROLE"))))
				.andExpect(jsonPath("$.errorCode").value("INVALID_WORKFLOW"));
		send(post("/api/v1/approval-workflows"), workflow("Unknown role", 20, tenDays,
				List.of(Map.of("approverType", "ROLE", "roleCode", "NOPE"))))
				.andExpect(jsonPath("$.detail").value("Unknown role NOPE"));
		send(post("/api/v1/approval-workflows"), workflow("Unknown employee", 20, tenDays,
				List.of(Map.of("approverType", "EMPLOYEE", "employeeId", UUID.randomUUID()))))
				.andExpect(jsonPath("$.errorCode").value("INVALID_WORKFLOW"));
		send(post("/api/v1/approval-workflows"), workflow("Unknown type", 20,
				List.of(Map.of("leaveTypeId", UUID.randomUUID())), managerStep))
				.andExpect(jsonPath("$.errorCode").value("INVALID_WORKFLOW"));
		send(post("/api/v1/approval-workflows"), workflow("No steps", 20, tenDays, List.of()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("steps"));
		send(post("/api/v1/approval-workflows"), workflow("Same priority", 10, tenDays, managerStep))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("WORKFLOW_PRIORITY_TAKEN"));

		String standard = getJson("/api/v1/approval-workflows").get(1).get("id").asString();
		send(put("/api/v1/approval-workflows/{id}", standard), workflow("Standard", 1000, tenDays, managerStep))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("DEFAULT_WORKFLOW_FIXED"));
		Map<String, Object> inactive = workflow("Standard", 1000, List.of(), managerStep);
		inactive.put("active", false);
		send(put("/api/v1/approval-workflows/{id}", standard), inactive).andExpect(status().isConflict());
		send(put("/api/v1/approval-workflows/{id}", standard), workflow("Standard", 1000, List.of(),
				List.of(Map.of("approverType", "REPORTING_MANAGER"), Map.of("approverType", "SKIP_LEVEL_MANAGER"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.steps[*].approverType", contains("REPORTING_MANAGER", "SKIP_LEVEL_MANAGER")));
	}

	private ResultActions approveFirstTask(String token) throws Exception {
		JsonNode tasks = jsonMapper.readTree(mockMvc.perform(get("/api/v1/approvals/tasks").header("Authorization", token))
				.andReturn().getResponse().getContentAsString());
		return mockMvc.perform(post("/api/v1/approvals/tasks/{id}/approve", tasks.get(0).get("taskId").asString())
				.header("Authorization", token));
	}

	private LocalDate nextMonday() {
		LocalDate day = fixtures.today(tenant).plusDays(14);
		while (day.getDayOfWeek() != DayOfWeek.MONDAY || day.plusDays(4).getYear() != day.getYear()) {
			day = day.plusDays(1);
		}
		return day;
	}

	private String tokenFor(EmployeeResponse employee) {
		return fixtures.bearer(tenant, fixtures.awaitSubject(tenant, employee.email()));
	}

	private JsonNode getJson(String path) throws Exception {
		return jsonMapper.readTree(mockMvc.perform(get(path).header("Authorization", admin))
				.andReturn().getResponse().getContentAsString());
	}

	private static Map<String, Object> workflow(String name, int priority, List<Map<String, Object>> rules,
			List<Map<String, Object>> steps) {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		body.put("priority", priority);
		body.put("rules", rules);
		body.put("steps", steps);
		return body;
	}

	private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
		return mockMvc.perform(request.header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(body)));
	}

}
