package com.bsolz.lms.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.approval.service.ApprovalDeadlineJob;
import com.bsolz.lms.approval.service.ApprovalService.DeadlineAction;
import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.support.ApiClient;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * Overdue approval steps: reminded, then escalated, then approved automatically. Carla manages Maya, who
 * manages Eli. The job runs with chosen times instead of waiting for hours to pass.
 */
@IntegrationTest
class ApprovalDeadlineTests {

	@Autowired
	ApiClient api;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	ApprovalDeadlineJob job;

	@Autowired
	Clock clock;

	TestTenant tenant;

	String admin;

	EmployeeResponse carla;

	EmployeeResponse maya;

	EmployeeResponse eli;

	@BeforeEach
	void setUp() {
		tenant = fixtures.newTenant("dl");
		admin = fixtures.adminBearer(tenant);
		UUID department = fixtures.createDepartment(tenant);
		carla = fixtures.createEmployee(tenant, "Carla", department, null);
		maya = fixtures.createEmployee(tenant, "Maya", department, carla.id());
		eli = fixtures.createEmployee(tenant, "Eli", department, maya.id());
		fixtures.allocateAroundToday(tenant);
	}

	@Test
	void overdueStepsAreRemindedEscalatedAndFinallyApproved() throws Exception {
		UUID casual = fixtures.leaveTypeId(tenant, "CASUAL");
		api.post(admin, "/api/v1/approval-workflows", workflow(casual, 1, 2, 3))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reminderAfterHours").value(1))
				.andExpect(jsonPath("$.autoApproveAfterHours").value(3));
		String eliToken = tokenFor(eli);
		String mayaToken = tokenFor(maya);
		String carlaToken = tokenFor(carla);
		Instant submitted = Instant.now(clock);
		LocalDate monday = ApiClient.mondayAhead(fixtures.today(tenant));
		String requestId = api.submitLeave(eliToken, casual, monday, monday).get("id").asString();

		assertThat(runAt(submitted.plus(Duration.ofMinutes(30)))).isEmpty();

		assertThat(runAt(submitted.plus(Duration.ofMinutes(61)))).containsEntry(DeadlineAction.REMINDED, 1);
		assertThat(awaitNotification(mayaToken, "APPROVAL_REMINDER").get("message").asString())
				.startsWith("Eli Tester's Casual Leave on ");
		// Reminded again only after another interval.
		assertThat(runAt(submitted.plus(Duration.ofMinutes(90)))).isEmpty();

		assertThat(runAt(submitted.plus(Duration.ofMinutes(121)))).containsEntry(DeadlineAction.ESCALATED, 1);
		assertThat(awaitNotification(carlaToken, "APPROVAL_ESCALATED").get("title").asString())
				.isEqualTo("Approval escalated to you");
		api.get(carlaToken, "/api/v1/approvals/tasks").andExpect(jsonPath("$[0].subjectId").value(requestId));

		assertThat(runAt(submitted.plus(Duration.ofMinutes(181)))).containsEntry(DeadlineAction.AUTO_APPROVED, 1);
		api.get(eliToken, "/api/v1/leave-requests/{id}", requestId)
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.approvals[0].steps[0].status").value("APPROVED"))
				.andExpect(jsonPath("$.approvals[0].steps[0].actedBy").doesNotExist())
				.andExpect(jsonPath("$.approvals[0].steps[0].comment")
						.value("Approved automatically after 3 hours without a decision"));
		assertThat(awaitNotification(eliToken, "LEAVE_APPROVED").get("message").asString())
				.endsWith("was approved automatically.");
		assertThat(runAt(submitted.plus(Duration.ofHours(10)))).isEmpty();
	}

	@Test
	void workflowsWithoutDeadlinesAreLeftAlone() throws Exception {
		UUID casual = fixtures.leaveTypeId(tenant, "CASUAL");
		api.post(admin, "/api/v1/approval-workflows", workflow(casual, 1, 5, 4))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Automatic approval must come after escalation"));
		api.post(admin, "/api/v1/approval-workflows", workflow(casual, null, null, null)).andExpect(status().isCreated());
		String eliToken = tokenFor(eli);
		LocalDate monday = ApiClient.mondayAhead(fixtures.today(tenant));
		String requestId = api.submitLeave(eliToken, casual, monday, monday).get("id").asString();

		assertThat(runAt(Instant.now(clock).plus(Duration.ofDays(20)))).isEmpty();
		api.get(eliToken, "/api/v1/leave-requests/{id}", requestId).andExpect(jsonPath("$.status").value("PENDING"));
	}

	private Map<DeadlineAction, Integer> runAt(Instant now) {
		Map<DeadlineAction, Integer> counts = TenantContext.call(tenant.info(), () -> job.run(now));
		counts.remove(DeadlineAction.NONE);
		return counts;
	}

	private JsonNode awaitNotification(String token, String type) throws Exception {
		await().atMost(Duration.ofSeconds(15)).until(() -> find(token, type) != null);
		return find(token, type);
	}

	private JsonNode find(String token, String type) throws Exception {
		return api.json(api.get(token, "/api/v1/notifications")).get("content").valueStream()
				.filter(notification -> notification.get("type").asString().equals(type))
				.findFirst().orElse(null);
	}

	private static Map<String, Object> workflow(UUID leaveTypeId, Integer remind, Integer escalate, Integer autoApprove) {
		Map<String, Object> body = new HashMap<>();
		body.put("name", "Casual " + UUID.randomUUID());
		body.put("priority", 5);
		body.put("rules", List.of(Map.of("leaveTypeId", leaveTypeId)));
		body.put("steps", List.of(Map.of("approverType", "REPORTING_MANAGER")));
		body.put("reminderAfterHours", remind);
		body.put("escalateAfterHours", escalate);
		body.put("autoApproveAfterHours", autoApprove);
		return body;
	}

	private String tokenFor(EmployeeResponse employee) {
		return fixtures.bearer(tenant, fixtures.awaitSubject(tenant, employee.email()));
	}

}
