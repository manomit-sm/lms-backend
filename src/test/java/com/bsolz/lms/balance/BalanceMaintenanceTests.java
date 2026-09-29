package com.bsolz.lms.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bsolz.lms.balance.api.BalanceApi;
import com.bsolz.lms.balance.api.BalanceSnapshot;
import com.bsolz.lms.balance.service.BalanceMaintenanceService;
import com.bsolz.lms.leavepolicy.api.LeavePeriodInfo;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.leavepolicy.model.enums.LeavePeriodStatus;
import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The balance job: opening periods ahead, accrual, year-end carry forward / lapse, and carried-forward
 * expiry. Run with chosen dates, so the results don't depend on today's date.
 */
@IntegrationTest
class BalanceMaintenanceTests {

	@Autowired
	TestFixtures fixtures;

	@Autowired
	BalanceMaintenanceService maintenance;

	@Autowired
	BalanceApi balanceApi;

	@Autowired
	LeavePolicyApi policyApi;

	@Autowired
	JdbcTemplate jdbcTemplate;

	TestTenant tenant;

	EmployeeResponse eli;

	LeavePeriodInfo current;

	@BeforeEach
	void setUp() {
		tenant = fixtures.newTenant("bm");
		eli = fixtures.createEmployee(tenant, "Eli", fixtures.createDepartment(tenant), null);
		fixtures.awaitAllocation(tenant, eli.id());
		current = fixtures.currentPeriod(tenant);
	}

	@Test
	void yearEndCarriesForwardUpToTheLimitAndLetsTheRestLapse() {
		fixtures.allocateAroundToday(tenant);
		LeavePeriodInfo previous = period(current.startDate().minusDays(1));
		UUID annual = fixtures.leaveTypeId(tenant, "ANNUAL");
		UUID sick = fixtures.leaveTypeId(tenant, "SICK");
		as(() -> balanceApi.adjust(eli.id(), annual, previous.id(), new BigDecimal("-3"), "Taken off the books"));

		BalanceMaintenanceService.Result result = as(() -> maintenance.run(current.startDate().plusDays(3)));

		assertThat(result.closedPeriods()).isEqualTo(1);
		assertThat(period(previous.startDate()).status()).isEqualTo(LeavePeriodStatus.CLOSED);
		// Annual: 18 - 3 = 15 left; the policy carries 10 and 5 lapse.
		BalanceSnapshot oldAnnual = balance(annual, previous);
		assertThat(oldAnnual.carriedOut()).isEqualByComparingTo("10");
		assertThat(oldAnnual.expired()).isEqualByComparingTo("5");
		assertThat(oldAnnual.available()).isEqualByComparingTo("0");
		BalanceSnapshot newAnnual = balance(annual, current);
		assertThat(newAnnual.carriedForward()).isEqualByComparingTo("10");
		assertThat(newAnnual.available()).isEqualByComparingTo("28");
		// Sick leave carries nothing.
		assertThat(balance(sick, previous).expired()).isEqualByComparingTo("10");
		assertThat(balance(sick, current).carriedForward()).isEqualByComparingTo("0");
		assertThat(ledger(oldAnnual.balanceId())).containsExactly("ALLOCATION:18.00", "ADJUSTMENT:-3.00",
				"CARRY_OUT:10.00", "EXPIRY:5.00");

		// Running again changes nothing.
		as(() -> maintenance.run(current.startDate().plusDays(4)));
		assertThat(balance(annual, current).carriedForward()).isEqualByComparingTo("10");
		assertThat(ledger(oldAnnual.balanceId())).hasSize(4);

		await().atMost(Duration.ofSeconds(15)).until(() -> count(
				"SELECT count(*) FROM activity_log WHERE action = 'LEAVE_PERIOD_CLOSED' AND entity_id = ?",
				previous.id()) == 1);
	}

	@Test
	void carriedForwardDaysExpireAfterThePolicysMonths() {
		fixtures.allocateAroundToday(tenant);
		UUID annual = fixtures.leaveTypeId(tenant, "ANNUAL");
		as(() -> jdbcTemplate.update(
				"UPDATE leave_policy SET carry_forward_expiry_months = 3 WHERE leave_type_id = ?", annual));

		as(() -> maintenance.run(current.startDate().plusMonths(3).minusDays(1)));
		assertThat(balance(annual, current).carriedForward()).isEqualByComparingTo("10");
		assertThat(balance(annual, current).expired()).isEqualByComparingTo("0");

		BalanceMaintenanceService.Result result = as(() -> maintenance.run(current.startDate().plusMonths(3)));
		assertThat(result.expiredBalances()).isEqualTo(1);
		assertThat(balance(annual, current).expired()).isEqualByComparingTo("10");
		assertThat(balance(annual, current).available()).isEqualByComparingTo("18");

		as(() -> maintenance.run(current.startDate().plusMonths(4)));
		assertThat(balance(annual, current).expired()).isEqualByComparingTo("10");
	}

	@Test
	void monthlyPoliciesAccrueEachMonthOnce() {
		UUID monthly = as(() -> {
			UUID type = jdbcTemplate.queryForObject("""
					INSERT INTO leave_type (code, name, color, paid, balance_tracked)
					VALUES ('EARNED', 'Earned Leave', '#111111', true, true) RETURNING id
					""", UUID.class);
			jdbcTemplate.update("""
					INSERT INTO leave_policy (leave_type_id, name, entitlement_days, accrual_method, prorate_on_joining)
					VALUES (?, 'Earned monthly', 12, 'MONTHLY', true)
					""", type);
			return type;
		});
		EmployeeResponse joiner = fixtures.createEmployee(tenant, "Jo", fixtures.createDepartment(tenant), null, null,
				null, current.startDate().plusMonths(1).plusDays(20));

		BalanceMaintenanceService.Result result = as(() -> maintenance.run(current.startDate().plusMonths(2).plusDays(3)));

		// Eli: January, February, March. The joiner (after the 15th of February): March only.
		assertThat(result.accrued()).isEqualTo(4);
		assertThat(balanceOf(eli.id(), monthly, current).allocated()).isEqualByComparingTo("3");
		assertThat(balanceOf(joiner.id(), monthly, current).allocated()).isEqualByComparingTo("1");

		assertThat(as(() -> maintenance.run(current.startDate().plusMonths(2).plusDays(9))).accrued()).isZero();
		as(() -> maintenance.run(current.startDate().plusMonths(5)));
		assertThat(balanceOf(eli.id(), monthly, current).allocated()).isEqualByComparingTo("6");
		assertThat(ledger(balanceOf(eli.id(), monthly, current).balanceId())).hasSize(6).allMatch(
				entry -> entry.equals("ACCRUAL:1.00"));
	}

	@Test
	void theNextLeaveYearOpensAMonthAheadWithBalances() {
		UUID annual = fixtures.leaveTypeId(tenant, "ANNUAL");
		assertThat(as(() -> policyApi.findPeriodContaining(current.endDate().plusDays(1)))).isEmpty();

		as(() -> maintenance.run(current.endDate().minusDays(40)));
		assertThat(as(() -> policyApi.findPeriodContaining(current.endDate().plusDays(1)))).isEmpty();

		BalanceMaintenanceService.Result result = as(() -> maintenance.run(current.endDate().minusDays(20)));
		LeavePeriodInfo next = period(current.endDate().plusDays(1));
		assertThat(next.startDate()).isEqualTo(current.endDate().plusDays(1));
		assertThat(next.endDate()).isEqualTo(current.endDate().plusYears(1));
		assertThat(result.allocated()).isPositive();
		assertThat(balance(annual, next).allocated()).isEqualByComparingTo("18");

		// Someone joining now gets the next year's allocation too.
		EmployeeResponse joiner = fixtures.createEmployee(tenant, "Late", fixtures.createDepartment(tenant), null);
		await().atMost(Duration.ofSeconds(15)).until(() -> as(() -> balanceApi.find(joiner.id(), annual, next.id()))
				.map(snapshot -> snapshot.allocated().signum() > 0).orElse(false));
	}

	private LeavePeriodInfo period(LocalDate date) {
		return as(() -> policyApi.findPeriodContaining(date).orElseThrow());
	}

	private BalanceSnapshot balance(UUID leaveTypeId, LeavePeriodInfo period) {
		return balanceOf(eli.id(), leaveTypeId, period);
	}

	private BalanceSnapshot balanceOf(UUID employeeId, UUID leaveTypeId, LeavePeriodInfo period) {
		return as(() -> balanceApi.find(employeeId, leaveTypeId, period.id()).orElseThrow());
	}

	private List<String> ledger(UUID balanceId) {
		return as(() -> jdbcTemplate.queryForList(
				"SELECT type || ':' || amount FROM leave_balance_transaction WHERE leave_balance_id = ? ORDER BY seq",
				String.class, balanceId));
	}

	private int count(String sql, Object... args) {
		return as(() -> jdbcTemplate.queryForObject(sql, Integer.class, args));
	}

	private <T> T as(ScopedValue.CallableOp<T, RuntimeException> work) {
		return TenantContext.call(tenant.info(), work);
	}

}
