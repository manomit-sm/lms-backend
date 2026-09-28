package com.bsolz.lms.support;

import static org.awaitility.Awaitility.await;

import com.bsolz.lms.identity.service.CurrentUserAccessService;
import com.bsolz.lms.leavepolicy.api.LeavePeriodInfo;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.organization.model.enums.EmploymentStatus;
import com.bsolz.lms.organization.model.enums.EmploymentType;
import com.bsolz.lms.organization.model.enums.Gender;
import com.bsolz.lms.organization.service.DepartmentService;
import com.bsolz.lms.organization.service.EmployeeService;
import com.bsolz.lms.organization.web.dto.DepartmentRequest;
import com.bsolz.lms.organization.web.dto.EmployeeRequest;
import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.platform.entity.Tenant;
import com.bsolz.lms.platform.service.TenantProvisioningService;
import com.bsolz.lms.platform.web.dto.CreateTenantRequest;
import com.bsolz.lms.shared.security.local.LocalTokenIssuer;
import com.bsolz.lms.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Test data setup through the application's own services. Tests share one database, so every key,
 * code and email is made unique.
 */
@Component
@RequiredArgsConstructor
public class TestFixtures {

	private static final Duration TOKEN_TTL = Duration.ofMinutes(10);

	private final TenantProvisioningService provisioning;

	private final DepartmentService departmentService;

	private final EmployeeService employeeService;

	private final CurrentUserAccessService accessService;

	private final LeavePolicyApi policyApi;

	private final LocalTokenIssuer tokens;

	private final Clock clock;

	private final JdbcTemplate jdbcTemplate;

	public static String unique(String prefix) {
		return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
	}

	public TestTenant newTenant(String prefix) {
		String key = unique(prefix);
		String adminEmail = "admin@" + key + ".test";
		Tenant tenant = provisioning
				.provision(new CreateTenantRequest(key, "Tenant " + key, null, "Europe/Brussels", adminEmail));
		TestTenant created = new TestTenant(tenant.toInfo(), adminEmail, null);
		return new TestTenant(created.info(), adminEmail, subjectOf(created, adminEmail));
	}

	public String bearer(TestTenant tenant, String subject) {
		return "Bearer " + tokens.issueTenantToken(subject, tenant.id(), TOKEN_TTL);
	}

	public String adminBearer(TestTenant tenant) {
		return bearer(tenant, tenant.adminSubject());
	}

	public String platformAdminBearer() {
		return "Bearer " + tokens.issuePlatformToken("platform-admin", true, TOKEN_TTL);
	}

	public UUID createDepartment(TestTenant tenant) {
		String code = unique("dep").toUpperCase(Locale.ROOT);
		return TenantContext.call(tenant.info(),
				() -> departmentService.create(new DepartmentRequest(code, "Department " + code, null, null, null)).id());
	}

	/** Creates an employee; their user is invited asynchronously - see {@link #awaitSubject}. */
	public EmployeeResponse createEmployee(TestTenant tenant, String firstName, UUID departmentId, UUID managerId) {
		return createEmployee(tenant, firstName, departmentId, managerId, null, null, LocalDate.of(2024, 1, 1));
	}

	/**
	 * Creates an employee with the attributes leave rules look at. Their user is invited and their
	 * balances allocated asynchronously - see {@link #awaitSubject} and {@link #awaitAllocation}.
	 */
	public EmployeeResponse createEmployee(TestTenant tenant, String firstName, UUID departmentId, UUID managerId,
			Gender gender, UUID locationId, LocalDate joiningDate) {
		String code = unique("E");
		String email = (firstName + "." + code + "@example.test").toLowerCase(Locale.ROOT);
		return TenantContext.call(tenant.info(),
				() -> employeeService.create(new EmployeeRequest(code, firstName, "Tester", email, null, gender,
						departmentId, null, locationId, managerId, null, EmploymentType.FULL_TIME,
						EmploymentStatus.ACTIVE, joiningDate, null)));
	}

	/** Waits until the employee's joining allocation has run (it always creates at least one balance). */
	public void awaitAllocation(TestTenant tenant, UUID employeeId) {
		await().atMost(Duration.ofSeconds(15)).until(() -> TenantContext.call(tenant.info(),
				() -> jdbcTemplate.queryForObject("SELECT count(*) FROM leave_balance WHERE employee_id = ?",
						Integer.class, employeeId)) > 0);
	}

	public UUID leaveTypeId(TestTenant tenant, String code) {
		return TenantContext.call(tenant.info(), () -> jdbcTemplate
				.queryForObject("SELECT id FROM leave_type WHERE code = ?", UUID.class, code));
	}

	/** The seeded leave period: the current calendar year. */
	public LeavePeriodInfo currentPeriod(TestTenant tenant) {
		return TenantContext.call(tenant.info(),
				() -> policyApi.findPeriodContaining(LocalDate.now(clock.withZone(tenant.info().timezone())))
						.orElseThrow());
	}

	public String subjectOf(TestTenant tenant, String email) {
		List<String> subjects = TenantContext.call(tenant.info(), () -> jdbcTemplate.queryForList(
				"SELECT idp_subject FROM app_user WHERE lower(email) = lower(?)", String.class, email));
		return subjects.isEmpty() ? null : subjects.getFirst();
	}

	public String awaitSubject(TestTenant tenant, String email) {
		await().atMost(Duration.ofSeconds(15)).until(() -> subjectOf(tenant, email) != null);
		return subjectOf(tenant, email);
	}

	public String userStatus(TestTenant tenant, String email) {
		return TenantContext.call(tenant.info(), () -> jdbcTemplate.queryForObject(
				"SELECT status FROM app_user WHERE lower(email) = lower(?)", String.class, email));
	}

	/** Adds system or custom roles to a user directly (bypassing the API's grant checks and access-change events). */
	public void grantRoles(TestTenant tenant, String email, String... roleCodes) {
		TenantContext.run(tenant.info(), () -> {
			for (String roleCode : roleCodes) {
				jdbcTemplate.update("""
						INSERT INTO user_role (user_id, role_id)
						SELECT u.id, r.id FROM app_user u, role r WHERE lower(u.email) = lower(?) AND r.code = ?
						ON CONFLICT DO NOTHING
						""", email, roleCode);
			}
		});
		accessService.evictTenant(tenant.id());
	}

}
