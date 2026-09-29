package com.bsolz.lms.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.shared.storage.InMemoryFileStorage;
import com.bsolz.lms.support.ApiClient;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Reports, the dashboard and exports. Maya (a manager) manages Eli and Zoe; Carla is HR; Omar is in
 * another department with no manager. Leave: Eli 2 days annual (approved) and 1 day rejected, Zoe 1 day
 * sick (approved) and 1 day casual (pending), Omar 1 day casual (approved automatically).
 */
@IntegrationTest
class ReportingApiTests {

	@Autowired
	ApiClient api;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	InMemoryFileStorage storage;

	TestTenant tenant;

	EmployeeResponse carla;

	EmployeeResponse maya;

	EmployeeResponse eli;

	EmployeeResponse zoe;

	EmployeeResponse omar;

	String carlaToken;

	String mayaToken;

	String eliToken;

	LocalDate today;

	LocalDate monday;

	@BeforeEach
	void setUp() throws Exception {
		tenant = fixtures.newTenant("rep");
		UUID department = fixtures.createDepartment(tenant);
		carla = fixtures.createEmployee(tenant, "Carla", department, null);
		maya = fixtures.createEmployee(tenant, "Maya", department, null);
		eli = fixtures.createEmployee(tenant, "Eli", department, maya.id());
		zoe = fixtures.createEmployee(tenant, "Zoe", department, maya.id());
		omar = fixtures.createEmployee(tenant, "Omar", fixtures.createDepartment(tenant), null);
		carlaToken = tokenFor(carla);
		mayaToken = tokenFor(maya);
		eliToken = tokenFor(eli);
		String zoeToken = tokenFor(zoe);
		String omarToken = tokenFor(omar);
		fixtures.grantRoles(tenant, maya.email(), "MANAGER");
		fixtures.grantRoles(tenant, carla.email(), "HR_ADMIN");
		fixtures.allocateAroundToday(tenant);
		today = fixtures.today(tenant);
		monday = ApiClient.mondayAhead(today);

		api.submitLeave(eliToken, type("ANNUAL"), monday, monday.plusDays(1));
		api.approveFirstTask(mayaToken);
		String rejected = api.submitLeave(eliToken, type("ANNUAL"), monday.plusDays(3), monday.plusDays(3))
				.get("id").asString();
		JsonNode tasks = api.json(api.get(mayaToken, "/api/v1/approvals/tasks"));
		api.post(mayaToken, "/api/v1/approvals/tasks/{id}/reject", Map.of("comment", "Busy week"),
				tasks.valueStream().filter(task -> task.get("subjectId").asString().equals(rejected)).findFirst()
						.orElseThrow().get("taskId").asString())
				.andExpect(status().isOk());
		api.submitLeave(zoeToken, type("SICK"), monday, monday);
		api.approveFirstTask(mayaToken);
		api.submitLeave(zoeToken, type("CASUAL"), monday.plusDays(2), monday.plusDays(2));
		api.submitLeave(omarToken, type("CASUAL"), monday.plusDays(1), monday.plusDays(1));
	}

	@Test
	void usageReportsCoverTheCallersScope() throws Exception {
		JsonNode organisation = report(carlaToken, "leave-type-usage");
		assertThat(organisation.get("scope").asString()).isEqualTo("TENANT");
		assertThat(usage(organisation, "ANNUAL").get("daysTaken").decimalValue()).isEqualByComparingTo("2");
		assertThat(usage(organisation, "ANNUAL").get("employees").asInt()).isEqualTo(1);
		assertThat(usage(organisation, "SICK").get("daysTaken").decimalValue()).isEqualByComparingTo("1");
		assertThat(usage(organisation, "CASUAL").get("daysTaken").decimalValue()).isEqualByComparingTo("1");

		JsonNode team = report(mayaToken, "leave-type-usage");
		assertThat(team.get("scope").asString()).isEqualTo("TEAM");
		assertThat(usage(team, "ANNUAL").get("daysTaken").decimalValue()).isEqualByComparingTo("2");
		assertThat(usage(team, "CASUAL").get("daysTaken").decimalValue()).isEqualByComparingTo("0");

		JsonNode departments = report(carlaToken, "department-usage").get("departments");
		JsonNode first = departments.valueStream()
				.filter(department -> department.get("departmentId").asString().equals(eli.department().id().toString()))
				.findFirst().orElseThrow();
		assertThat(first.get("headcount").asInt()).isEqualTo(4);
		assertThat(first.get("daysTaken").decimalValue()).isEqualByComparingTo("3");
		assertThat(first.get("averageDaysPerEmployee").decimalValue()).isEqualByComparingTo("0.75");
		assertThat(departments).hasSize(2);
	}

	@Test
	void monthlyStatisticsAndDecisionsCountEveryRequest() throws Exception {
		JsonNode months = report(carlaToken, "monthly-statistics").get("months");
		assertThat(months.valueStream().mapToInt(month -> month.get("submitted").asInt()).sum()).isEqualTo(5);
		assertThat(months.valueStream().mapToInt(month -> month.get("approved").asInt()).sum()).isEqualTo(3);
		assertThat(months.valueStream().mapToInt(month -> month.get("rejected").asInt()).sum()).isEqualTo(1);
		assertThat(months.valueStream().map(month -> month.get("daysTaken").decimalValue())
				.reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("4");

		JsonNode total = report(carlaToken, "decisions").get("total");
		assertThat(total.get("submitted").asInt()).isEqualTo(5);
		assertThat(total.get("approved").asInt()).isEqualTo(3);
		assertThat(total.get("rejected").asInt()).isEqualTo(1);
		assertThat(total.get("pending").asInt()).isEqualTo(1);
		assertThat(total.get("approvalRate").decimalValue()).isEqualByComparingTo("0.75");
	}

	@Test
	void historyAndBalancesRespectTheReportingLine() throws Exception {
		api.get(mayaToken, "/api/v1/reports/employee-leave-history?from={f}&to={t}&employeeId={e}", today,
				monday.plusDays(30), omar.id())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("EMPLOYEE_NOT_VISIBLE"));
		api.get(mayaToken, "/api/v1/reports/employee-leave-history?from={f}&to={t}&employeeId={e}", today,
				monday.plusDays(30), eli.id())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].status").value("REJECTED"))
				.andExpect(jsonPath("$.content[1].leaveType.code").value("ANNUAL"));
		api.get(mayaToken, "/api/v1/reports/employee-leave-history?from={f}&to={t}", today, monday.plusDays(30))
				.andExpect(jsonPath("$.totalElements").value(4));

		api.get(carlaToken, "/api/v1/reports/balances?employeeId={e}&leaveTypeId={t}", eli.id(), type("ANNUAL"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].used").value(2.0))
				.andExpect(jsonPath("$.content[0].available").value(16.0));
		api.get(mayaToken, "/api/v1/reports/balances").andExpect(jsonPath("$.content[*].employeeName",
				everyItem(in(List.of("Maya Tester", "Eli Tester", "Zoe Tester")))));
	}

	@Test
	void dashboardsDependOnWhoIsLooking() throws Exception {
		JsonNode eliDashboard = api.json(api.get(eliToken, "/api/v1/dashboard/summary"));
		assertThat(eliDashboard.get("team").isNull()).isTrue();
		assertThat(eliDashboard.get("approvals").get("pendingTasks").asInt()).isZero();
		JsonNode annual = eliDashboard.get("me").get("balances").valueStream()
				.filter(balance -> balance.get("leaveType").get("code").asString().equals("ANNUAL")).findFirst()
				.orElseThrow();
		assertThat(annual.get("available").decimalValue()).isEqualByComparingTo("16");
		assertThat(eliDashboard.get("me").get("upcomingLeave").get(0).get("status").asString()).isEqualTo("APPROVED");

		JsonNode mayaDashboard = api.json(api.get(mayaToken, "/api/v1/dashboard/summary"));
		assertThat(mayaDashboard.get("approvals").get("pendingTasks").asInt()).isEqualTo(1);
		assertThat(mayaDashboard.get("team").get("scope").asString()).isEqualTo("TEAM");
		assertThat(mayaDashboard.get("team").get("headcount").asInt()).isEqualTo(3);
		assertThat(mayaDashboard.get("team").get("pendingRequests").asInt()).isEqualTo(1);

		JsonNode hrDashboard = api.json(api.get(carlaToken, "/api/v1/dashboard/summary"));
		assertThat(hrDashboard.get("team").get("scope").asString()).isEqualTo("TENANT");
		assertThat(hrDashboard.get("team").get("headcount").asInt()).isEqualTo(5);
	}

	@Test
	void exportsAreGeneratedInTheBackgroundAndDownloadedFromStorage() throws Exception {
		JsonNode csv = requestExport(Map.of("report", "LEAVE_TYPE_USAGE", "format", "CSV", "from", today.toString(),
				"to", monday.plusDays(30).toString()));
		assertThat(csv.get("status").asString()).isIn("PENDING", "RUNNING", "COMPLETED");
		JsonNode done = awaitCompleted(csv.get("id").asString());
		assertThat(done.get("downloadUrl").asString()).isNotBlank();
		assertThat(done.get("fileName").asString()).startsWith("leave-type-usage-").endsWith(".csv");
		String content = new String(storedContent(csv.get("id").asString(), done.get("fileName").asString()),
				StandardCharsets.UTF_8);
		assertThat(content).startsWith("﻿Leave type,Requests,Employees,Days taken\r\n")
				.contains("Annual Leave,1,1,2\r\n");

		Map<String, Object> balances = new HashMap<>();
		balances.put("report", "BALANCES");
		balances.put("format", "XLSX");
		JsonNode xlsx = requestExport(balances);
		JsonNode xlsxDone = awaitCompleted(xlsx.get("id").asString());
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
				storedContent(xlsx.get("id").asString(), xlsxDone.get("fileName").asString())))) {
			Sheet sheet = workbook.getSheet("Balances");
			assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Employee code");
			assertThat(sheet.getLastRowNum()).isEqualTo(xlsxDone.get("rowCount").asInt());
		}

		api.get(carlaToken, "/api/v1/reports/exports").andExpect(jsonPath("$.length()").value(2));
		api.get(fixtures.adminBearer(tenant), "/api/v1/reports/exports/{id}", csv.get("id").asString())
				.andExpect(status().isNotFound());
	}

	private JsonNode requestExport(Map<String, Object> body) throws Exception {
		return api.json(api.post(carlaToken, "/api/v1/reports/exports", body).andExpect(status().isAccepted()));
	}

	private JsonNode awaitCompleted(String exportId) throws Exception {
		await().atMost(Duration.ofSeconds(15)).until(() -> api.json(
				api.get(carlaToken, "/api/v1/reports/exports/{id}", exportId)).get("status").asString()
				.equals("COMPLETED"));
		return api.json(api.get(carlaToken, "/api/v1/reports/exports/{id}", exportId));
	}

	private byte[] storedContent(String exportId, String fileName) {
		return storage.content("tenants/" + tenant.id() + "/report-exports/" + exportId + "/" + fileName).orElseThrow();
	}

	private JsonNode report(String token, String name) throws Exception {
		return api.json(api.get(token, "/api/v1/reports/" + name + "?from={f}&to={t}", today, monday.plusDays(30))
				.andExpect(status().isOk()));
	}

	private static JsonNode usage(JsonNode report, String code) {
		return report.get("leaveTypes").valueStream()
				.filter(type -> type.get("leaveType").get("code").asString().equals(code)).findFirst().orElseThrow();
	}

	private UUID type(String code) {
		return fixtures.leaveTypeId(tenant, code);
	}

	private String tokenFor(EmployeeResponse employee) {
		return fixtures.bearer(tenant, fixtures.awaitSubject(tenant, employee.email()));
	}

}
