package com.bsolz.lms.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.balance.api.BalanceApi;
import com.bsolz.lms.balance.api.BalanceSnapshot;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.math.BigDecimal;
import java.time.DayOfWeek;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Leave requests end to end: rules, approval, balances. Maya manages Eli and Hana; Hana is also HR
 * (HR_ADMIN). Seeded rules: annual leave (18 days) needs 7 days notice; five days or more go to the
 * "Long leave" workflow (manager, then HR); shorter requests to "Standard" (manager).
 */
@IntegrationTest
class LeaveRequestApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	BalanceApi balanceApi;

	@Autowired
	LeavePolicyApi policyApi;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	JsonMapper jsonMapper;

	TestTenant tenant;

	String admin;

	UUID department;

	EmployeeResponse maya;

	EmployeeResponse eli;

	EmployeeResponse hana;

	String mayaToken;

	String eliToken;

	String hanaToken;

	LocalDate today;

	@BeforeEach
	void setUp() {
		tenant = fixtures.newTenant("lv");
		admin = fixtures.adminBearer(tenant);
		department = fixtures.createDepartment(tenant);
		maya = fixtures.createEmployee(tenant, "Maya", department, null);
		eli = fixtures.createEmployee(tenant, "Eli", department, maya.id());
		hana = fixtures.createEmployee(tenant, "Hana", department, maya.id());
		mayaToken = tokenFor(maya);
		eliToken = tokenFor(eli);
		hanaToken = tokenFor(hana);
		fixtures.grantRoles(tenant, maya.email(), "MANAGER");
		fixtures.grantRoles(tenant, hana.email(), "HR_ADMIN");
		fixtures.allocateAroundToday(tenant);
		today = fixtures.today(tenant);
	}

	@Test
	void shortLeaveIsApprovedByTheManager() throws Exception {
		LocalDate monday = monday(14, 2);
		String id = id(apply(eliToken, "ANNUAL", monday, monday.plusDays(1))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.totalDays").value(2.0))
				.andExpect(jsonPath("$.approvals[0].workflowName").value("Standard"))
				.andExpect(jsonPath("$.approvals[0].steps[0].status").value("PENDING"))
				.andExpect(jsonPath("$.approvals[0].steps[0].assignees[0].name").value(maya.fullName())));
		assertBalance(eli, "ANNUAL", monday, "2", "0");

		mockMvc.perform(get("/api/v1/leave-requests/pending-approval").header("Authorization", mayaToken))
				.andExpect(jsonPath("$[0].subjectType").value("LEAVE_REQUEST"))
				.andExpect(jsonPath("$[0].leaveRequest.id").value(id))
				.andExpect(jsonPath("$[0].leaveRequest.employee.name").value(eli.fullName()));
		decide(mayaToken, "approve", "Enjoy").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.steps[0].actedBy.name").value(maya.fullName()));

		getRequest(eliToken, id).andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.decidedAt").isNotEmpty())
				.andExpect(jsonPath("$.history[*].action", contains("SUBMIT", "APPROVE")));
		assertBalance(eli, "ANNUAL", monday, "0", "2");
	}

	@Test
	void longLeaveNeedsTheManagerThenHr() throws Exception {
		LocalDate monday = monday(14, 5);
		String id = id(apply(eliToken, "ANNUAL", monday, monday.plusDays(4))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.totalDays").value(5.0))
				.andExpect(jsonPath("$.approvals[0].workflowName").value("Long leave"))
				.andExpect(jsonPath("$.approvals[0].steps[*].status", contains("PENDING", "WAITING")))
				.andExpect(jsonPath("$.approvals[0].steps[1].assignees[*].name", contains(hana.fullName()))));

		// HR's step isn't current yet.
		String hrTask = getJson(eliToken, "/api/v1/leave-requests/" + id).at("/approvals/0/steps/1/taskId").asString();
		mockMvc.perform(post("/api/v1/approvals/tasks/{id}/approve", hrTask).header("Authorization", hanaToken))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("TASK_NOT_PENDING"));

		decide(mayaToken, "approve", null).andExpect(jsonPath("$.status").value("PENDING"));
		getRequest(eliToken, id).andExpect(jsonPath("$.status").value("PENDING"));
		assertBalance(eli, "ANNUAL", monday, "5", "0");

		decide(hanaToken, "approve", "OK from HR").andExpect(jsonPath("$.status").value("APPROVED"));
		getRequest(eliToken, id).andExpect(jsonPath("$.status").value("APPROVED"));
		assertBalance(eli, "ANNUAL", monday, "0", "5");
	}

	@Test
	void rejectionGivesTheDaysBack() throws Exception {
		LocalDate monday = monday(14, 2);
		String id = id(apply(eliToken, "ANNUAL", monday, monday.plusDays(1)).andExpect(status().isCreated()));

		decide(mayaToken, "reject", " ").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("COMMENT_REQUIRED"));
		decide(mayaToken, "reject", "Release week").andExpect(jsonPath("$.status").value("REJECTED"));

		getRequest(eliToken, id).andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.history[1].comment").value("Release week"));
		assertBalance(eli, "ANNUAL", monday, "0", "0");
		assertThat(available(eli, "ANNUAL", monday)).isEqualByComparingTo("18");
	}

	@Test
	void withdrawingAPendingRequestReleasesItsDaysAndStopsTheApproval() throws Exception {
		LocalDate monday = monday(14, 2);
		String id = id(apply(eliToken, "ANNUAL", monday, monday.plusDays(1)).andExpect(status().isCreated()));

		mockMvc.perform(post("/api/v1/leave-requests/{id}/withdraw", id).header("Authorization", hanaToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("NOT_REQUESTER"));
		mockMvc.perform(post("/api/v1/leave-requests/{id}/withdraw", id).header("Authorization", eliToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("WITHDRAWN"))
				.andExpect(jsonPath("$.approvals[0].status").value("CANCELLED"));
		mockMvc.perform(post("/api/v1/leave-requests/{id}/withdraw", id).header("Authorization", eliToken))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

		mockMvc.perform(get("/api/v1/leave-requests/pending-approval").header("Authorization", mayaToken))
				.andExpect(jsonPath("$.length()").value(0));
		assertThat(available(eli, "ANNUAL", monday)).isEqualByComparingTo("18");
	}

	@Test
	void cancellingBeforeTheLeaveStartsGivesTheDaysBack() throws Exception {
		LocalDate monday = monday(14, 2);
		String id = id(apply(eliToken, "ANNUAL", monday, monday.plusDays(1)).andExpect(status().isCreated()));
		decide(mayaToken, "approve", null);

		cancel(eliToken, id, "Plans changed").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.cancellationReason").value("Plans changed"));
		assertBalance(eli, "ANNUAL", monday, "0", "0");
		cancel(eliToken, id, "Again").andExpect(status().isConflict());
	}

	@Test
	void cancellingStartedLeaveNeedsApproval() throws Exception {
		LocalDate day = lastWorkday();
		String id = id(apply(eliToken, "SICK", day, day).andExpect(status().isCreated()));
		decide(mayaToken, "approve", null);
		assertBalance(eli, "SICK", day, "0", "1");

		cancel(eliToken, id, "Felt better").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLATION_PENDING"))
				.andExpect(jsonPath("$.approvals[1].subjectType").value("LEAVE_CANCELLATION"));
		mockMvc.perform(get("/api/v1/leave-requests/pending-approval").header("Authorization", mayaToken))
				.andExpect(jsonPath("$[0].subjectType").value("LEAVE_CANCELLATION"));
		decide(mayaToken, "reject", "You were off sick").andExpect(jsonPath("$.status").value("REJECTED"));
		getRequest(eliToken, id).andExpect(jsonPath("$.status").value("APPROVED"));
		assertBalance(eli, "SICK", day, "0", "1");

		cancel(eliToken, id, "Really felt better").andExpect(jsonPath("$.status").value("CANCELLATION_PENDING"));
		decide(mayaToken, "approve", null).andExpect(jsonPath("$.status").value("APPROVED"));
		getRequest(eliToken, id).andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.history[*].action", contains("SUBMIT", "APPROVE", "REQUEST_CANCELLATION",
						"REJECT_CANCELLATION", "REQUEST_CANCELLATION", "APPROVE_CANCELLATION")));
		assertBalance(eli, "SICK", day, "0", "0");
	}

	@Test
	void hrCancelsStartedLeaveWithoutApproval() throws Exception {
		LocalDate day = lastWorkday();
		String id = id(apply(eliToken, "SICK", day, day).andExpect(status().isCreated()));
		decide(mayaToken, "approve", null);

		cancel(hanaToken, id, "Entered by mistake").andExpect(jsonPath("$.status").value("CANCELLED"));
		assertBalance(eli, "SICK", day, "0", "0");
	}

	@Test
	void overlappingLeaveIsRefused() throws Exception {
		LocalDate monday = monday(14, 3);
		String first = id(apply(eliToken, "ANNUAL", monday, monday.plusDays(1)).andExpect(status().isCreated()));

		apply(eliToken, "WFH", monday.plusDays(1), monday.plusDays(2)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("OVERLAPPING_LEAVE"));
		mockMvc.perform(post("/api/v1/leave-requests/{id}/withdraw", first).header("Authorization", eliToken));
		apply(eliToken, "WFH", monday.plusDays(1), monday.plusDays(2)).andExpect(status().isCreated());
	}

	@Test
	void theBalanceLimitsBalanceTrackedLeave() throws Exception {
		LocalDate monday = monday(14, 26);
		// Four weeks of annual leave: 20 working days, 18 available.
		preview(eliToken, "ANNUAL", monday, monday.plusDays(25)).andExpect(jsonPath("$.totalDays").value(20.0))
				.andExpect(jsonPath("$.available").value(18.0))
				.andExpect(jsonPath("$.availableAfter").value(-2.0))
				.andExpect(jsonPath("$.violations[*].code", contains("INSUFFICIENT_BALANCE")));
		apply(eliToken, "ANNUAL", monday, monday.plusDays(25)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_BALANCE"));
		assertBalance(eli, "ANNUAL", monday, "0", "0");
	}

	@Test
	void unpaidLeaveAndWorkFromHomeAreNeverLimited() throws Exception {
		LocalDate monday = monday(14, 26);
		preview(eliToken, "UNPAID", monday, monday.plusDays(25)).andExpect(jsonPath("$.valid").value(true))
				.andExpect(jsonPath("$.totalDays").value(20.0))
				.andExpect(jsonPath("$.available").doesNotExist());
		apply(eliToken, "UNPAID", monday, monday.plusDays(25)).andExpect(status().isCreated());
		decide(mayaToken, "approve", null);
		decide(hanaToken, "approve", null).andExpect(jsonPath("$.status").value("APPROVED"));

		LocalDate fridayBefore = monday.minusDays(3);
		apply(eliToken, "WFH", fridayBefore, fridayBefore).andExpect(status().isCreated());
		assertThat(TenantContext.call(tenant.info(), () -> jdbcTemplate.queryForObject("""
				SELECT count(*) FROM leave_balance b JOIN leave_type t ON t.id = b.leave_type_id
				WHERE b.employee_id = ? AND t.code IN ('UNPAID', 'WFH')
				""", Integer.class, eli.id()))).isZero();
	}

	@Test
	void previewShowsTheDaysAndEveryRuleBroken() throws Exception {
		LocalDate monday = monday(14, 11);
		mockMvc.perform(post("/api/v1/holidays").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(Map.of("name", "Founders day", "date", monday.plusDays(2)))))
				.andExpect(status().isCreated());

		// Mon-Fri with a Wednesday holiday: 4 days. Fri afternoon to Mon midday: 0.5 + weekend + 0.5.
		preview(eliToken, "ANNUAL", monday, monday.plusDays(4)).andExpect(jsonPath("$.valid").value(true))
				.andExpect(jsonPath("$.totalDays").value(4.0))
				.andExpect(jsonPath("$.days[2].dayType").value("HOLIDAY"))
				.andExpect(jsonPath("$.days[2].amount").value(0.0));
		previewSessions(eliToken, "ANNUAL", monday.plusDays(4), monday.plusDays(7), "SECOND_HALF", "FIRST_HALF")
				.andExpect(jsonPath("$.totalDays").value(1.0))
				.andExpect(jsonPath("$.days[*].dayType", contains("WORKING", "WEEKEND", "WEEKEND", "WORKING")));

		preview(eliToken, "CASUAL", monday.plusDays(7), monday.plusDays(10))
				.andExpect(jsonPath("$.violations[*].code", contains("MAX_CONSECUTIVE_DAYS_EXCEEDED")));
		preview(eliToken, "ANNUAL", today.plusDays(1), today.plusDays(1))
				.andExpect(jsonPath("$.violations[*].code", hasItems("INSUFFICIENT_NOTICE")));
		// Starting today is short notice; starting before today is backdating.
		preview(eliToken, "ANNUAL", today.minusWeeks(1), today.minusWeeks(1))
				.andExpect(jsonPath("$.violations[*].code", hasItems("BACKDATING_NOT_ALLOWED")));
		preview(eliToken, "SICK", monday.plusDays(7), monday.plusDays(9))
				.andExpect(jsonPath("$.violations[*].code", contains("ATTACHMENT_REQUIRED")));
		previewSessions(eliToken, "MATERNITY", monday, monday, "FIRST_HALF", "FIRST_HALF")
				.andExpect(jsonPath("$.violations[*].code", contains("HALF_DAY_NOT_ALLOWED", "NOT_ELIGIBLE")));
		preview(eliToken, "ANNUAL", monday.plusDays(5), monday.plusDays(6))
				.andExpect(jsonPath("$.violations[*].code", contains("NO_WORKING_DAYS")));
		LocalDate yearEnd = fixtures.currentPeriod(tenant).endDate();
		preview(eliToken, "UNPAID", yearEnd.minusDays(1), yearEnd.plusDays(2))
				.andExpect(jsonPath("$.violations[*].code", hasItems("CROSSES_LEAVE_PERIOD")));

		// Submitting refuses with the first violation and lists them all.
		applySessions(eliToken, "MATERNITY", monday, monday, "FIRST_HALF", "FIRST_HALF", List.of())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.errorCode").value("HALF_DAY_NOT_ALLOWED"))
				.andExpect(jsonPath("$.violations[*].code", contains("HALF_DAY_NOT_ALLOWED", "NOT_ELIGIBLE")));
		applySessions(eliToken, "ANNUAL", monday, monday.plusDays(1), "FIRST_HALF", "FULL_DAY", List.of())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_SESSIONS"));
	}

	@Test
	void probationRulesOutAnnualLeave() throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("employeeCode", TestFixtures.unique("P"));
		body.put("firstName", "Pia");
		body.put("lastName", "Tester");
		body.put("email", TestFixtures.unique("pia") + "@example.test");
		body.put("departmentId", department);
		body.put("reportingManagerId", maya.id());
		body.put("employmentType", "FULL_TIME");
		body.put("employmentStatus", "PROBATION");
		body.put("joiningDate", "2024-01-01");
		body.put("probationEndDate", today.plusYears(2).toString());
		String email = getJson(admin, post("/api/v1/employees"), body).get("email").asString();
		String piaToken = fixtures.bearer(tenant, fixtures.awaitSubject(tenant, email));
		fixtures.allocateAroundToday(tenant);

		LocalDate monday = monday(14, 1);
		preview(piaToken, "ANNUAL", monday, monday).andExpect(jsonPath("$.violations[*].code",
				contains("NOT_ALLOWED_DURING_PROBATION")));
		preview(piaToken, "CASUAL", monday, monday).andExpect(jsonPath("$.valid").value(true));
	}

	@Test
	void documentsSatisfyTheAttachmentRule() throws Exception {
		LocalDate monday = monday(14, 3);
		mockMvc.perform(post("/api/v1/leave-requests/attachments").header("Authorization", eliToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(Map.of("fileName", "note.docx", "contentType",
						"application/msword", "sizeBytes", 1000))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_ATTACHMENT"));
		JsonNode upload = getJson(eliToken, post("/api/v1/leave-requests/attachments"),
				Map.of("fileName", "Doctor's note.pdf", "contentType", "application/pdf", "sizeBytes", 20_000));
		assertThat(upload.get("method").asString()).isEqualTo("PUT");
		assertThat(upload.get("uploadUrl").asString()).contains("tenants/" + tenant.id() + "/leave-attachments/")
				.endsWith("Doctor_s_note.pdf");
		String attachmentId = upload.get("attachmentId").asString();

		// Someone else can't use Eli's upload.
		applySessions(hanaToken, "SICK", monday, monday.plusDays(2), null, null, List.of(attachmentId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("ATTACHMENT_NOT_FOUND"));
		String id = id(applySessions(eliToken, "SICK", monday, monday.plusDays(2), null, null, List.of(attachmentId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.attachments[0].fileName").value("Doctor's note.pdf")));
		mockMvc.perform(get("/api/v1/leave-requests/{id}/attachments/{a}/download", id, attachmentId)
				.header("Authorization", mayaToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").isNotEmpty());
		// Used once only.
		applySessions(eliToken, "SICK", monday.plusDays(7), monday.plusDays(9), null, null, List.of(attachmentId))
				.andExpect(status().isNotFound());
	}

	@Test
	void leaveIsApprovedAutomaticallyWhenNobodyCanApprove() throws Exception {
		LocalDate monday = monday(14, 2);
		apply(mayaToken, "ANNUAL", monday, monday.plusDays(1)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.approvals[0].steps[0].status").value("SKIPPED"))
				.andExpect(jsonPath("$.approvals[0].steps[0].comment").value("The requester has no reporting manager"))
				.andExpect(jsonPath("$.history[1].actorUserId").doesNotExist());
		assertBalance(maya, "ANNUAL", monday, "0", "2");
	}

	@Test
	void nobodyApprovesTheirOwnLeave() throws Exception {
		LocalDate monday = monday(14, 5);
		// Hana is the only HR admin, so the HR step of her own long leave has nobody else and is skipped.
		apply(hanaToken, "ANNUAL", monday, monday.plusDays(4)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.approvals[0].steps[1].status").value("SKIPPED"))
				.andExpect(jsonPath("$.approvals[0].steps[1].comment").value("Only the requester could approve this step"));
		decide(mayaToken, "approve", null).andExpect(jsonPath("$.status").value("APPROVED"));
	}

	@Test
	void onlyAssignedApproversDecideAndOnlyInvolvedPeopleSeeTheRequest() throws Exception {
		EmployeeResponse otto = fixtures.createEmployee(tenant, "Otto", department, null);
		String ottoToken = tokenFor(otto);
		LocalDate monday = monday(14, 2);
		String id = id(apply(eliToken, "ANNUAL", monday, monday.plusDays(1)).andExpect(status().isCreated()));
		String task = getJson(mayaToken, "/api/v1/leave-requests/pending-approval").get(0).get("taskId").asString();

		mockMvc.perform(post("/api/v1/approvals/tasks/{id}/approve", task).header("Authorization", ottoToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("NOT_ASSIGNED"));
		getRequest(ottoToken, id).andExpect(status().isForbidden());
		getRequest(mayaToken, id).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/leave-requests").header("Authorization", ottoToken))
				.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/v1/leave-requests").header("Authorization", mayaToken))
				.andExpect(jsonPath("$.content[*].id", contains(id)));
		mockMvc.perform(get("/api/v1/leave-requests?employeeId={e}", eli.id()).header("Authorization", ottoToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/leave-requests?status=PENDING").header("Authorization", hanaToken))
				.andExpect(jsonPath("$.content[*].id", contains(id)));
	}

	private void assertBalance(EmployeeResponse employee, String typeCode, LocalDate date, String pending, String used) {
		BalanceSnapshot balance = balance(employee, typeCode, date).orElseThrow();
		assertThat(balance.pending()).as("pending").isEqualByComparingTo(pending);
		assertThat(balance.used()).as("used").isEqualByComparingTo(used);
	}

	private BigDecimal available(EmployeeResponse employee, String typeCode, LocalDate date) {
		return balance(employee, typeCode, date).orElseThrow().available();
	}

	private Optional<BalanceSnapshot> balance(EmployeeResponse employee, String typeCode, LocalDate date) {
		return TenantContext.call(tenant.info(), () -> balanceApi.find(employee.id(),
				fixtures.leaveTypeId(tenant, typeCode), policyApi.findPeriodContaining(date).orElseThrow().id()));
	}

	/** A Monday at least {@code daysAhead} from today, with the following {@code span} days in the same year. */
	private LocalDate monday(int daysAhead, int span) {
		LocalDate day = today.plusDays(daysAhead);
		while (day.getDayOfWeek() != DayOfWeek.MONDAY) {
			day = day.plusDays(1);
		}
		if (day.plusDays(span).getYear() != day.getYear()) {
			day = LocalDate.of(day.getYear() + 1, 1, 1);
			while (day.getDayOfWeek() != DayOfWeek.MONDAY) {
				day = day.plusDays(1);
			}
		}
		return day;
	}

	/** Today, or the last weekday before it: leave on this day has started. */
	private LocalDate lastWorkday() {
		LocalDate day = today;
		while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
			day = day.minusDays(1);
		}
		return day;
	}

	private String tokenFor(EmployeeResponse employee) {
		return fixtures.bearer(tenant, fixtures.awaitSubject(tenant, employee.email()));
	}

	private ResultActions apply(String token, String typeCode, LocalDate start, LocalDate end) throws Exception {
		return applySessions(token, typeCode, start, end, null, null, List.of());
	}

	private ResultActions applySessions(String token, String typeCode, LocalDate start, LocalDate end,
			String startSession, String endSession, List<String> attachmentIds) throws Exception {
		return mockMvc.perform(post("/api/v1/leave-requests").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(application(typeCode, start, end, startSession, endSession,
						attachmentIds))));
	}

	private ResultActions preview(String token, String typeCode, LocalDate start, LocalDate end) throws Exception {
		return previewSessions(token, typeCode, start, end, null, null);
	}

	private ResultActions previewSessions(String token, String typeCode, LocalDate start, LocalDate end,
			String startSession, String endSession) throws Exception {
		return mockMvc.perform(post("/api/v1/leave-requests/preview").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(application(typeCode, start, end, startSession, endSession,
						List.of()))))
				.andExpect(status().isOk());
	}

	private Map<String, Object> application(String typeCode, LocalDate start, LocalDate end, String startSession,
			String endSession, List<String> attachmentIds) {
		Map<String, Object> body = new HashMap<>();
		body.put("leaveTypeId", fixtures.leaveTypeId(tenant, typeCode));
		body.put("startDate", start.toString());
		body.put("endDate", end.toString());
		body.put("startSession", startSession);
		body.put("endSession", endSession);
		body.put("attachmentIds", attachmentIds);
		return body;
	}

	/** Decides the caller's oldest pending task. */
	private ResultActions decide(String token, String decision, String comment) throws Exception {
		String task = getJson(token, "/api/v1/approvals/tasks").get(0).get("taskId").asString();
		Map<String, Object> body = new HashMap<>();
		body.put("comment", comment);
		return mockMvc.perform(post("/api/v1/approvals/tasks/{id}/{decision}", task, decision)
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(body)));
	}

	private ResultActions cancel(String token, String id, String reason) throws Exception {
		return mockMvc.perform(post("/api/v1/leave-requests/{id}/cancel", id).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(Map.of("reason", reason))));
	}

	private ResultActions getRequest(String token, String id) throws Exception {
		return mockMvc.perform(get("/api/v1/leave-requests/{id}", id).header("Authorization", token));
	}

	private JsonNode getJson(String token, String path) throws Exception {
		return jsonMapper.readTree(mockMvc.perform(get(path).header("Authorization", token))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private JsonNode getJson(String token, MockHttpServletRequestBuilder request, Object body) throws Exception {
		return jsonMapper.readTree(mockMvc.perform(request.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(body)))
				.andExpect(status().is2xxSuccessful())
				.andReturn().getResponse().getContentAsString());
	}

	private String id(ResultActions result) throws Exception {
		return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asString();
	}

}
