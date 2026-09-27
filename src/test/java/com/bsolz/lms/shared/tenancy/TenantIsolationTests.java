package com.bsolz.lms.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Data written while one tenant is bound is invisible to every other tenant - through JPA, through
 * JdbcTemplate, in async tasks and in per-tenant jobs.
 */
@IntegrationTest
class TenantIsolationTests {

	@Autowired
	TestFixtures fixtures;

	@Autowired
	IsolationProbeRepository probes;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	TenantJobRunner jobRunner;

	@Autowired
	@Qualifier("applicationTaskExecutor")
	AsyncTaskExecutor taskExecutor;

	TenantInfo acme;

	TenantInfo globex;

	@BeforeEach
	void provisionTwoTenants() {
		acme = fixtures.newTenant("acme").info();
		globex = fixtures.newTenant("globex").info();
		for (TenantInfo tenant : new TenantInfo[] { acme, globex }) {
			TenantContext.run(tenant, () -> jdbcTemplate.execute(
					"CREATE TABLE isolation_probe (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), label TEXT)"));
		}
	}

	@Test
	void jpaWritesAreVisibleOnlyToTheirTenant() {
		UUID acmeProbeId = TenantContext.call(acme, () -> probes.save(new IsolationProbe("acme-jpa")).getId());

		TenantContext.run(globex, () -> {
			assertThat(probes.count()).isZero();
			assertThat(probes.findById(acmeProbeId)).isEmpty();
		});
		TenantContext.run(acme, () -> assertThat(probes.findById(acmeProbeId)).isPresent());
	}

	@Test
	void jdbcWritesAreVisibleOnlyToTheirTenant() {
		TenantContext.run(acme, () -> jdbcTemplate.update("INSERT INTO isolation_probe (label) VALUES ('acme-jdbc')"));

		TenantContext.run(globex, () -> assertThat(countProbesViaJdbc()).isZero());
		TenantContext.run(acme, () -> assertThat(countProbesViaJdbc()).isOne());
	}

	@Test
	void jpaAndJdbcShareTheTenantSchemaInsideOneTransaction() {
		TenantContext.run(acme, () -> transactionTemplate.executeWithoutResult(status -> {
			probes.saveAndFlush(new IsolationProbe("in-tx"));
			assertThat(countProbesViaJdbc()).isOne();
		}));
		TenantContext.run(globex, () -> assertThat(probes.count()).isZero());
	}

	@Test
	void noTenantMeansNoAccessToTenantTables() {
		assertThatThrownBy(() -> probes.count()).isInstanceOf(RuntimeException.class);
		assertThatThrownBy(this::countProbesViaJdbc).isInstanceOf(RuntimeException.class);
	}

	@Test
	void asyncTasksRunAsTheSubmittingTenant() throws Exception {
		TenantContext.run(acme, () -> probes.save(new IsolationProbe("acme-async")));

		Future<String> result = TenantContext.call(acme,
				() -> taskExecutor.submit(() -> TenantContext.require().key() + ":" + probes.count()));

		assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(acme.key() + ":1");
	}

	@Test
	void jobRunnerBindsEachTenantInTurnAndIsolatesFailures() {
		TenantContext.run(acme, () -> probes.save(new IsolationProbe("acme-job")));
		Map<String, Long> countsByTenant = new ConcurrentHashMap<>();

		// Tenants created by other test classes have no isolation_probe table: their iterations fail
		// without affecting acme and globex.
		TenantJobRunner.JobRunResult result = jobRunner.runForEachTenant("count-probes",
				tenant -> countsByTenant.put(tenant.key(), probes.count()));

		assertThat(countsByTenant).containsEntry(acme.key(), 1L).containsEntry(globex.key(), 0L);
		assertThat(result.failedTenantIds()).doesNotContain(acme.id(), globex.id());
	}

	private Integer countProbesViaJdbc() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM isolation_probe", Integer.class);
	}

}
