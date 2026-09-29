package com.bsolz.lms.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.approval.service.ApprovalDeadlineJob;
import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.support.ApiClient;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Asynchronous listeners and scheduled jobs, which run without a request, write to the right tenant's
 * schema: two tenants go through the same flow and each schema only ever holds its own rows.
 */
@IntegrationTest
class TenantJobIsolationTests {

	@Autowired
	ApiClient api;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TenantJobRunner jobRunner;

	@Autowired
	ApprovalDeadlineJob deadlineJob;

	@Autowired
	EventPublicationJobs eventPublicationJobs;

	@Autowired
	FlakyProbeListener probeListener;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	Clock clock;

	Side first;

	Side second;

	@BeforeEach
	void setUp() {
		first = new Side(fixtures.newTenant("iso-a"));
		second = new Side(fixtures.newTenant("iso-b"));
	}

	@Test
	void eventListenersWriteToTheTenantThatPublished() throws Exception {
		String firstRequest = first.submitLeave("ANNUAL");
		String secondRequest = second.submitLeave("ANNUAL");

		for (Side side : List.of(first, second)) {
			String own = side == first ? firstRequest : secondRequest;
			String other = side == first ? secondRequest : firstRequest;
			await().atMost(Duration.ofSeconds(15)).until(() -> side.count(
					"SELECT count(*) FROM notification WHERE subject_id = ?::uuid", own) == 1
					&& side.count("SELECT count(*) FROM activity_log WHERE entity_id = ?::uuid", own) == 1);
			assertThat(side.count("SELECT count(*) FROM notification WHERE subject_id = ?::uuid", other)).isZero();
			assertThat(side.count("SELECT count(*) FROM activity_log WHERE entity_id = ?::uuid", other)).isZero();
			// Both listeners' deliveries were recorded - and completed - in the tenant's own registry.
			await().atMost(Duration.ofSeconds(15)).until(() -> side.count("""
					SELECT count(*) FROM event_publication
					WHERE serialized_event LIKE '%' || ? || '%' AND completion_date IS NOT NULL
					  AND event_type LIKE '%LeaveRequestStatusChanged'
					""", own) == 2);
		}
		assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM public.event_publication WHERE serialized_event LIKE ? OR serialized_event LIKE ?",
				Integer.class, "%" + firstRequest + "%", "%" + secondRequest + "%")).isZero();
	}

	@Test
	void scheduledJobsWorkOnEachTenantInItsOwnSchema() throws Exception {
		first.remindCasualApprovalsAfterAnHour();
		second.remindCasualApprovalsAfterAnHour();
		Instant submitted = Instant.now(clock);
		String firstRequest = first.submitLeave("CASUAL");
		String secondRequest = second.submitLeave("CASUAL");

		TenantJobRunner.JobRunResult result = jobRunner.runForEachTenant("approval-deadlines",
				tenant -> deadlineJob.run(submitted.plus(Duration.ofMinutes(61))));

		assertThat(result.failedTenantIds()).doesNotContain(first.tenant.id(), second.tenant.id());
		for (Side side : List.of(first, second)) {
			String own = side == first ? firstRequest : secondRequest;
			assertThat(side.count("""
					SELECT count(*) FROM approval_task t JOIN approval_request r ON r.id = t.approval_request_id
					WHERE r.subject_id = ?::uuid AND t.last_reminded_at IS NOT NULL
					""", own)).isEqualTo(1);
			await().atMost(Duration.ofSeconds(15)).until(() -> side.count(
					"SELECT count(*) FROM notification WHERE type = 'APPROVAL_REMINDER' AND subject_id = ?::uuid",
					own) == 1);
			assertThat(side.count("SELECT count(*) FROM notification WHERE type = 'APPROVAL_REMINDER'")).isEqualTo(1);
		}
	}

	@Test
	void failedDeliveriesAreResubmittedWithinTheirTenant() {
		UUID probeId = UUID.randomUUID();
		TenantContext.run(first.tenant.info(), () -> transactionTemplate.executeWithoutResult(
				status -> events.publishEvent(new ProbeEvent(first.tenant.id(), probeId))));
		String incomplete = "SELECT count(*) FROM event_publication WHERE serialized_event LIKE '%' || ? || '%'"
				+ " AND completion_date IS NULL";
		await().atMost(Duration.ofSeconds(15)).until(() -> probeListener.attempts(probeId) == 1
				&& first.count(incomplete, probeId.toString()) == 1);

		eventPublicationJobs.resubmitIncomplete(Duration.ZERO);

		await().atMost(Duration.ofSeconds(15)).until(() -> probeListener.attempts(probeId) == 2
				&& first.count(incomplete, probeId.toString()) == 0);
		assertThat(probeListener.tenantKeyOfLastDelivery(probeId)).isEqualTo(first.tenant.info().key());
		String anyRow = "SELECT count(*) FROM event_publication WHERE serialized_event LIKE '%' || ? || '%'";
		assertThat(first.count(anyRow, probeId.toString())).isEqualTo(1);
		assertThat(second.count(anyRow, probeId.toString())).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.event_publication WHERE serialized_event"
				+ " LIKE ?", Integer.class, "%" + probeId + "%")).isZero();
	}

	/** One tenant with a manager and their report. */
	private final class Side {

		final TestTenant tenant;

		final String employeeToken;

		Side(TestTenant tenant) {
			this.tenant = tenant;
			UUID department = fixtures.createDepartment(tenant);
			EmployeeResponse manager = fixtures.createEmployee(tenant, "Maya", department, null);
			EmployeeResponse employee = fixtures.createEmployee(tenant, "Eli", department, manager.id());
			fixtures.awaitSubject(tenant, manager.email());
			this.employeeToken = fixtures.bearer(tenant, fixtures.awaitSubject(tenant, employee.email()));
			fixtures.allocateAroundToday(tenant);
		}

		String submitLeave(String leaveTypeCode) throws Exception {
			LocalDate monday = ApiClient.mondayAhead(fixtures.today(tenant));
			return api.submitLeave(employeeToken, fixtures.leaveTypeId(tenant, leaveTypeCode), monday, monday)
					.get("id").asString();
		}

		void remindCasualApprovalsAfterAnHour() throws Exception {
			api.post(fixtures.adminBearer(tenant), "/api/v1/approval-workflows", Map.of("name", "Casual", "priority", 5,
					"rules", List.of(Map.of("leaveTypeId", fixtures.leaveTypeId(tenant, "CASUAL"))), "steps",
					List.of(Map.of("approverType", "REPORTING_MANAGER")), "reminderAfterHours", 1))
					.andExpect(status().isCreated());
		}

		int count(String sql, Object... args) {
			return TenantContext.call(tenant.info(), () -> jdbcTemplate.queryForObject(sql, Integer.class, args));
		}

	}

}
