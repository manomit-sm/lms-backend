package com.bsolz.lms.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.notification.service.UpcomingLeaveNotifier;
import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.support.ApiClient;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** In-app notifications: who hears about what, reading them, the SSE stream and upcoming-leave reminders. */
@IntegrationTest
class NotificationApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ApiClient api;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	UpcomingLeaveNotifier upcomingLeaveNotifier;

	TestTenant tenant;

	EmployeeResponse maya;

	EmployeeResponse eli;

	String mayaToken;

	String eliToken;

	UUID annual;

	@BeforeEach
	void setUp() {
		tenant = fixtures.newTenant("ntf");
		UUID department = fixtures.createDepartment(tenant);
		maya = fixtures.createEmployee(tenant, "Maya", department, null);
		eli = fixtures.createEmployee(tenant, "Eli", department, maya.id());
		mayaToken = tokenFor(maya);
		eliToken = tokenFor(eli);
		fixtures.allocateAroundToday(tenant);
		annual = fixtures.leaveTypeId(tenant, "ANNUAL");
	}

	@Test
	void approversAndRequestersAreNotified() throws Exception {
		LocalDate monday = ApiClient.mondayAhead(fixtures.today(tenant));
		String requestId = api.submitLeave(eliToken, annual, monday, monday.plusDays(1)).get("id").asString();

		JsonNode toApprove = awaitNotifications(mayaToken, 1).get(0);
		assertThat(toApprove.get("type").asString()).isEqualTo("APPROVAL_REQUESTED");
		assertThat(toApprove.get("message").asString()).startsWith("Eli Tester requested Annual Leave on ")
				.endsWith("(2 days).");
		assertThat(toApprove.get("subjectType").asString()).isEqualTo("LEAVE_REQUEST");
		assertThat(toApprove.get("subjectId").asString()).isEqualTo(requestId);
		// Nobody hears about their own actions.
		api.get(eliToken, "/api/v1/notifications/unread-count").andExpect(jsonPath("$.count").value(0));

		api.approveFirstTask(mayaToken);
		JsonNode approved = awaitNotifications(eliToken, 1).get(0);
		assertThat(approved.get("type").asString()).isEqualTo("LEAVE_APPROVED");
		assertThat(approved.get("message").asString()).endsWith("was approved by Maya Tester.");
		api.get(mayaToken, "/api/v1/notifications/unread-count").andExpect(jsonPath("$.count").value(1));
	}

	@Test
	void usersReadOnlyTheirOwnNotifications() throws Exception {
		LocalDate monday = ApiClient.mondayAhead(fixtures.today(tenant));
		api.submitLeave(eliToken, annual, monday, monday);
		api.submitLeave(eliToken, annual, monday.plusDays(2), monday.plusDays(2));
		JsonNode notifications = awaitNotifications(mayaToken, 2);
		String first = notifications.get(0).get("id").asString();

		api.post(eliToken, "/api/v1/notifications/{id}/read", null, first).andExpect(status().isNotFound());
		api.post(mayaToken, "/api/v1/notifications/{id}/read", null, first).andExpect(status().isNoContent());
		api.get(mayaToken, "/api/v1/notifications/unread-count").andExpect(jsonPath("$.count").value(1));
		api.get(mayaToken, "/api/v1/notifications?unreadOnly=true")
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].readAt").doesNotExist());
		api.get(mayaToken, "/api/v1/notifications")
				.andExpect(jsonPath("$.content[?(@.id == '" + first + "')].readAt").isNotEmpty());

		api.post(mayaToken, "/api/v1/notifications/read-all", null).andExpect(jsonPath("$.updated").value(1));
		api.get(mayaToken, "/api/v1/notifications/unread-count").andExpect(jsonPath("$.count").value(0));
	}

	@Test
	void newNotificationsArePushedToOpenStreams() throws Exception {
		MvcResult stream = mockMvc.perform(get("/api/v1/notifications/stream").header("Authorization", mayaToken))
				.andExpect(request().asyncStarted())
				.andReturn();
		assertThat(stream.getResponse().getContentType()).startsWith("text/event-stream");
		await().atMost(Duration.ofSeconds(5)).until(() -> stream.getResponse().getContentAsString()
				.contains("event:unread-count"));

		LocalDate monday = ApiClient.mondayAhead(fixtures.today(tenant));
		api.submitLeave(eliToken, annual, monday, monday);

		await().atMost(Duration.ofSeconds(15)).until(() -> stream.getResponse().getContentAsString()
				.contains("event:notification"));
		assertThat(stream.getResponse().getContentAsString()).contains("\"type\":\"APPROVAL_REQUESTED\"")
				.contains("Eli Tester requested Annual Leave");
	}

	@Test
	void employeesAndManagersHearAboutLeaveStartingTomorrow() throws Exception {
		LocalDate monday = ApiClient.mondayAhead(fixtures.today(tenant));
		api.submitLeave(eliToken, annual, monday, monday.plusDays(2));
		api.approveFirstTask(mayaToken);
		awaitNotifications(eliToken, 1);
		ZoneId zone = tenant.info().timezone();

		// Before 08:00 the day before, nothing yet; from 08:00, once.
		assertThat(runUpcoming(monday.minusDays(1).atTime(LocalTime.of(7, 59)).atZone(zone).toInstant())).isZero();
		assertThat(runUpcoming(monday.minusDays(1).atTime(LocalTime.of(8, 0)).atZone(zone).toInstant())).isEqualTo(1);
		runUpcoming(monday.minusDays(1).atTime(LocalTime.of(9, 0)).atZone(zone).toInstant());

		JsonNode eliNotifications = awaitNotifications(eliToken, 2);
		assertThat(eliNotifications.get(0).get("type").asString()).isEqualTo("LEAVE_STARTING");
		assertThat(eliNotifications.get(0).get("title").asString()).isEqualTo("Your leave starts tomorrow");
		JsonNode mayaNotifications = api.json(api.get(mayaToken, "/api/v1/notifications")).get("content");
		assertThat(mayaNotifications.get(0).get("title").asString()).isEqualTo("Eli Tester is on leave from tomorrow");
		assertThat(countOfType(mayaNotifications, "LEAVE_STARTING")).isEqualTo(1);
	}

	private int runUpcoming(Instant at) {
		return TenantContext.call(tenant.info(), () -> upcomingLeaveNotifier.run(at));
	}

	private JsonNode awaitNotifications(String token, int count) throws Exception {
		await().atMost(Duration.ofSeconds(15)).until(() -> api.json(api.get(token, "/api/v1/notifications"))
				.get("totalElements").asInt() >= count);
		return api.json(api.get(token, "/api/v1/notifications")).get("content");
	}

	private static long countOfType(JsonNode notifications, String type) {
		return notifications.valueStream()
				.filter(notification -> notification.get("type").asString().equals(type)).count();
	}

	private String tokenFor(EmployeeResponse employee) {
		return fixtures.bearer(tenant, fixtures.awaitSubject(tenant, employee.email()));
	}

}
