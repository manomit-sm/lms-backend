package com.bsolz.lms.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.balance.api.BalanceApi;
import com.bsolz.lms.balance.api.BalanceSnapshot;
import com.bsolz.lms.balance.api.HoldRequest;
import com.bsolz.lms.leavepolicy.api.LeavePeriodInfo;
import com.bsolz.lms.organization.model.enums.Gender;
import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class BalanceApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	BalanceApi balanceApi;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	JsonMapper jsonMapper;

	TestTenant tenant;

	String admin;

	UUID department;

	LeavePeriodInfo period;

	UUID annual;

	@BeforeEach
	void setUp() {
		tenant = fixtures.newTenant("bal");
		admin = fixtures.adminBearer(tenant);
		department = fixtures.createDepartment(tenant);
		period = fixtures.currentPeriod(tenant);
		annual = fixtures.leaveTypeId(tenant, "ANNUAL");
	}

	@Test
	void newEmployeesGetProratedBalancesForTheLeaveTypesTheyAreEligibleFor() throws Exception {
		// Joining on the 1st of the seventh month: 6 of 12 months.
		EmployeeResponse joiner = fixtures.createEmployee(tenant, "Jo", department, null, Gender.FEMALE, null,
				period.startDate().plusMonths(6));
		EmployeeResponse veteran = fixtures.createEmployee(tenant, "Val", department, null, Gender.MALE, null,
				period.startDate().minusYears(3));
		fixtures.awaitAllocation(tenant, joiner.id());
		fixtures.awaitAllocation(tenant, veteran.id());

		// Seeded entitlements: annual 18, casual 8, sick 10 (prorated); maternity 182 / paternity 10 (not prorated).
		assertThat(allocatedByType(joiner.id())).containsExactlyInAnyOrderEntriesOf(Map.of("ANNUAL", "9.00",
				"CASUAL", "4.00", "SICK", "5.00", "MATERNITY", "182.00"));
		assertThat(allocatedByType(veteran.id())).containsExactlyInAnyOrderEntriesOf(Map.of("ANNUAL", "18.00",
				"CASUAL", "8.00", "SICK", "10.00", "PATERNITY", "10.00"));

		String token = fixtures.bearer(tenant, fixtures.awaitSubject(tenant, joiner.email()));
		mockMvc.perform(get("/api/v1/balances").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].leaveType.code", contains("ANNUAL", "CASUAL", "SICK", "MATERNITY")))
				.andExpect(jsonPath("$[0].available").value(9.0))
				.andExpect(jsonPath("$[0].leavePeriod.id").value(period.id().toString()));
		String balanceId = balanceId(joiner.id(), annual).toString();
		mockMvc.perform(get("/api/v1/balances/{id}/transactions", balanceId).header("Authorization", token))
				.andExpect(jsonPath("$.content[0].type").value("ALLOCATION"))
				.andExpect(jsonPath("$.content[0].referenceType").value("EMPLOYEE_JOINING"))
				.andExpect(jsonPath("$.content[0].note")
						.value("Default Annual Leave: prorated from 18.00 (joined " + joiner.joiningDate() + ")"));
	}

	@Test
	void theLedgerAccountsForEveryChange() {
		UUID employee = newEmployee();
		UUID request1 = UUID.randomUUID();
		UUID request2 = UUID.randomUUID();

		asTenant(() -> {
			assertThat(hold(employee, annual, "3", request1)).hasValueSatisfying(balance -> {
				assertThat(balance.pending()).isEqualByComparingTo("3");
				assertThat(balance.available()).isEqualByComparingTo("15");
			});
			balanceApi.consume(request1);
			balanceApi.consume(request1); // idempotent
			balanceApi.release(request1); // nothing left to release
			hold(employee, annual, "2.5", request2);
			balanceApi.release(request2);
			balanceApi.reverse(request1);
			balanceApi.reverse(request1);
		});

		BalanceSnapshot balance = snapshot(employee, annual);
		assertThat(balance.used()).isEqualByComparingTo("0");
		assertThat(balance.pending()).isEqualByComparingTo("0");
		assertThat(balance.available()).isEqualByComparingTo("18");
		assertThat(ledgerTypes(employee, annual))
				.containsExactly("ALLOCATION", "HOLD", "CONSUME", "HOLD", "RELEASE", "REVERSAL");
		assertBucketsEqualLedger(employee, annual);
	}

	@Test
	void holdsCannotOverspendEvenWhenConcurrent() throws Exception {
		UUID employee = newEmployee();
		UUID casual = fixtures.leaveTypeId(tenant, "CASUAL"); // 8 days
		int attempts = 20;
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
			for (int i = 0; i < attempts; i++) {
				Callable<Boolean> attempt = () -> {
					start.await();
					try {
						TenantContext.run(tenant.info(), () -> hold(employee, casual, "1", UUID.randomUUID()));
						return true;
					}
					catch (ApiException ex) {
						assertThat(ex.getErrorCode().code()).isEqualTo("INSUFFICIENT_BALANCE");
						return false;
					}
				};
				results.add(pool.submit(attempt));
			}
			start.countDown();
			int succeeded = 0;
			for (Future<Boolean> result : results) {
				succeeded += result.get() ? 1 : 0;
			}
			assertThat(succeeded).isEqualTo(8);
		}
		BalanceSnapshot balance = snapshot(employee, casual);
		assertThat(balance.pending()).isEqualByComparingTo("8");
		assertThat(balance.available()).isEqualByComparingTo("0");
		assertBucketsEqualLedger(employee, casual);
	}

	@Test
	void holdsRespectEligibilityAndTracking() {
		UUID employee = newEmployee(); // gender not recorded: no maternity or paternity
		UUID unpaid = fixtures.leaveTypeId(tenant, "UNPAID");
		UUID request = UUID.randomUUID();

		asTenant(() -> {
			assertThat(hold(employee, unpaid, "30", request)).isEmpty();
			balanceApi.consume(request); // nothing held: nothing to do
			assertFailsWith("NOT_ELIGIBLE", () -> hold(employee, fixtures.leaveTypeId(tenant, "MATERNITY"), "1", UUID.randomUUID()));
			assertFailsWith("INSUFFICIENT_BALANCE", () -> hold(employee, annual, "18.5", UUID.randomUUID()));
			assertFailsWith("INVALID_AMOUNT", () -> hold(employee, annual, "0.25", UUID.randomUUID()));
			UUID twice = UUID.randomUUID();
			hold(employee, annual, "1", twice);
			assertFailsWith("ALREADY_HELD", () -> hold(employee, annual, "1", twice));
			assertFailsWith("NO_LEAVE_PERIOD", () -> balanceApi.hold(new HoldRequest(employee, annual,
					period.endDate().plusYears(5), BigDecimal.ONE, UUID.randomUUID())));
		});
		assertThat(TenantContext.call(tenant.info(), () -> jdbcTemplate.queryForObject(
				"SELECT count(*) FROM leave_balance WHERE employee_id = ? AND leave_type_id = ?", Integer.class,
				employee, unpaid))).isZero();
	}

	@Test
	void theLedgerIsAppendOnly() {
		UUID employee = newEmployee();
		UUID balanceId = balanceId(employee, annual);

		assertThatThrownBy(() -> TenantContext.run(tenant.info(), () -> jdbcTemplate.update(
				"UPDATE leave_balance_transaction SET amount = 100 WHERE leave_balance_id = ?", balanceId)))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("append-only");
		assertThatThrownBy(() -> TenantContext.run(tenant.info(), () -> jdbcTemplate.update(
				"DELETE FROM leave_balance_transaction WHERE leave_balance_id = ?", balanceId)))
				.hasMessageContaining("append-only");
	}

	@Test
	void hrAdjustsBalancesWithAReason() throws Exception {
		UUID employee = newEmployee();

		adjust(admin, employee, 2.5, "Worked on a public holiday").andExpect(status().isOk())
				.andExpect(jsonPath("$.adjusted").value(2.5))
				.andExpect(jsonPath("$.available").value(20.5));
		adjust(admin, employee, -21, "Too much").andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_BALANCE"))
				.andExpect(jsonPath("$.available").value(20.5));
		adjust(admin, employee, 0.3, "Odd").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("amount"));
		adjust(admin, employee, 1, " ").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("reason"));

		mockMvc.perform(get("/api/v1/balances/{id}/transactions", balanceId(employee, annual)).header("Authorization", admin))
				.andExpect(jsonPath("$.content[0].type").value("ADJUSTMENT"))
				.andExpect(jsonPath("$.content[0].note").value("Worked on a public holiday"))
				.andExpect(jsonPath("$.content[0].availableAfter").value(20.5))
				.andExpect(jsonPath("$.content[0].createdBy").isNotEmpty());
		assertBucketsEqualLedger(employee, annual);
	}

	@Test
	void bulkAllocationIsIdempotent() throws Exception {
		UUID employee = newEmployee();
		allocate(Map.of()).andExpect(status().isOk())
				.andExpect(jsonPath("$.leavePeriodId").value(period.id().toString()))
				.andExpect(jsonPath("$.balancesAllocated").value(0))
				.andExpect(jsonPath("$.alreadyAllocated").value(3));

		String nextPeriod = jsonMapper.readTree(mockMvc.perform(post("/api/v1/leave-periods")
				.header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString()).get("id").asString();
		allocate(Map.of("leavePeriodId", nextPeriod, "leaveTypeIds", List.of(annual))).andExpect(status().isOk())
				.andExpect(jsonPath("$.balancesAllocated").value(1));
		allocate(Map.of("leavePeriodId", nextPeriod)).andExpect(status().isOk())
				.andExpect(jsonPath("$.balancesAllocated").value(2))
				.andExpect(jsonPath("$.alreadyAllocated").value(1));
		mockMvc.perform(get("/api/v1/balances?employeeId={e}&leavePeriodId={p}", employee, nextPeriod)
				.header("Authorization", admin))
				.andExpect(jsonPath("$[0].leaveType.code").value("ANNUAL"))
				.andExpect(jsonPath("$[0].allocated").value(18.0));

		allocate(Map.of("leaveTypeIds", List.of(fixtures.leaveTypeId(tenant, "UNPAID")))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("LEAVE_TYPE_NOT_TRACKED"));
	}

	@Test
	void balancesFollowTheEmployeeDataScope() throws Exception {
		EmployeeResponse manager = fixtures.createEmployee(tenant, "Maya", department, null);
		EmployeeResponse report = fixtures.createEmployee(tenant, "Ravi", department, manager.id());
		EmployeeResponse outsider = fixtures.createEmployee(tenant, "Otto", department, null);
		fixtures.awaitAllocation(tenant, report.id());
		String managerToken = fixtures.bearer(tenant, fixtures.awaitSubject(tenant, manager.email()));
		String outsiderToken = fixtures.bearer(tenant, fixtures.awaitSubject(tenant, outsider.email()));
		fixtures.grantRoles(tenant, manager.email(), "MANAGER");

		mockMvc.perform(get("/api/v1/balances?employeeId={id}", report.id()).header("Authorization", managerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3));
		mockMvc.perform(get("/api/v1/balances?employeeId={id}", report.id()).header("Authorization", outsiderToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/balances/{id}/transactions", balanceId(report.id(), annual))
				.header("Authorization", outsiderToken))
				.andExpect(status().isForbidden());
		adjust(managerToken, report.id(), 1, "Nice work").andExpect(status().isForbidden());
		allocateAs(outsiderToken, Map.of()).andExpect(status().isForbidden());

		// Another tenant's admin sees nothing of this tenant's balances, even by id.
		TestTenant other = fixtures.newTenant("bal-other");
		mockMvc.perform(get("/api/v1/balances?employeeId={id}", report.id()).header("Authorization", fixtures.adminBearer(other)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/v1/balances/{id}/transactions", balanceId(report.id(), annual))
				.header("Authorization", fixtures.adminBearer(other)))
				.andExpect(status().isNotFound());
	}

	/** Each bucket equals the sum of its ledger entries, and the database's generated column agrees with the entity. */
	private void assertBucketsEqualLedger(UUID employeeId, UUID leaveTypeId) {
		Map<String, Object> row = TenantContext.call(tenant.info(), () -> jdbcTemplate.queryForMap("""
				SELECT b.allocated, b.carried_forward, b.adjusted, b.expired, b.used, b.pending, b.available,
				       coalesce(sum(t.amount) FILTER (WHERE t.type IN ('ALLOCATION', 'ACCRUAL')), 0) AS l_allocated,
				       coalesce(sum(t.amount) FILTER (WHERE t.type = 'CARRY_FORWARD'), 0) AS l_carried_forward,
				       coalesce(sum(t.amount) FILTER (WHERE t.type = 'ADJUSTMENT'), 0) AS l_adjusted,
				       coalesce(sum(t.amount) FILTER (WHERE t.type = 'EXPIRY'), 0) AS l_expired,
				       coalesce(sum(CASE t.type WHEN 'CONSUME' THEN t.amount WHEN 'REVERSAL' THEN -t.amount END), 0) AS l_used,
				       coalesce(sum(CASE t.type WHEN 'HOLD' THEN t.amount WHEN 'RELEASE' THEN -t.amount
				                                WHEN 'CONSUME' THEN -t.amount END), 0) AS l_pending,
				       (SELECT available_after FROM leave_balance_transaction
				        WHERE leave_balance_id = b.id ORDER BY seq DESC LIMIT 1) AS last_available_after
				FROM leave_balance b LEFT JOIN leave_balance_transaction t ON t.leave_balance_id = b.id
				WHERE b.employee_id = ? AND b.leave_type_id = ? AND b.leave_period_id = ?
				GROUP BY b.id
				""", employeeId, leaveTypeId, period.id()));
		for (String bucket : List.of("allocated", "carried_forward", "adjusted", "expired", "used", "pending")) {
			assertThat((BigDecimal) row.get(bucket)).as(bucket).isEqualByComparingTo((BigDecimal) row.get("l_" + bucket));
		}
		BigDecimal available = snapshot(employeeId, leaveTypeId).available();
		assertThat((BigDecimal) row.get("available")).isEqualByComparingTo(available);
		assertThat((BigDecimal) row.get("last_available_after")).isEqualByComparingTo(available);
	}

	private UUID newEmployee() {
		EmployeeResponse employee = fixtures.createEmployee(tenant, "Emp", department, null);
		fixtures.awaitAllocation(tenant, employee.id());
		return employee.id();
	}

	private Optional<BalanceSnapshot> hold(UUID employeeId, UUID leaveTypeId, String days, UUID requestId) {
		return balanceApi.hold(new HoldRequest(employeeId, leaveTypeId, period.startDate().plusMonths(1),
				new BigDecimal(days), requestId));
	}

	private BalanceSnapshot snapshot(UUID employeeId, UUID leaveTypeId) {
		return TenantContext.call(tenant.info(), () -> balanceApi.find(employeeId, leaveTypeId, period.id()).orElseThrow());
	}

	private UUID balanceId(UUID employeeId, UUID leaveTypeId) {
		return snapshot(employeeId, leaveTypeId).balanceId();
	}

	private Map<String, String> allocatedByType(UUID employeeId) {
		Map<String, String> allocated = new HashMap<>();
		TenantContext.run(tenant.info(), () -> jdbcTemplate.query("""
				SELECT t.code, b.allocated FROM leave_balance b JOIN leave_type t ON t.id = b.leave_type_id
				WHERE b.employee_id = ? AND b.leave_period_id = ?
				""", rs -> {
			allocated.put(rs.getString("code"), rs.getBigDecimal("allocated").toPlainString());
		}, employeeId, period.id()));
		return allocated;
	}

	private List<String> ledgerTypes(UUID employeeId, UUID leaveTypeId) {
		return TenantContext.call(tenant.info(), () -> jdbcTemplate.queryForList("""
				SELECT t.type FROM leave_balance_transaction t JOIN leave_balance b ON b.id = t.leave_balance_id
				WHERE b.employee_id = ? AND b.leave_type_id = ? ORDER BY t.seq
				""", String.class, employeeId, leaveTypeId));
	}

	private static void assertFailsWith(String errorCode, ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getErrorCode().code()).isEqualTo(errorCode));
	}

	private void asTenant(Runnable work) {
		TenantContext.run(tenant.info(), work);
	}

	private ResultActions adjust(String bearer, UUID employeeId, double amount, String reason) throws Exception {
		return mockMvc.perform(post("/api/v1/balances/adjustments").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(Map.of("employeeId", employeeId, "leaveTypeId", annual,
						"amount", amount, "reason", reason))));
	}

	private ResultActions allocate(Map<String, Object> body) throws Exception {
		return allocateAs(admin, body);
	}

	private ResultActions allocateAs(String bearer, Map<String, Object> body) throws Exception {
		return mockMvc.perform(post("/api/v1/balances/allocations").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(body)));
	}

}
