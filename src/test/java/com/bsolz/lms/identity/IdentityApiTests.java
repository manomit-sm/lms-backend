package com.bsolz.lms.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.shared.security.Permissions;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class IdentityApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	JsonMapper jsonMapper;

	TestTenant tenant;

	String admin;

	@BeforeEach
	void setUp() {
		tenant = fixtures.newTenant("idn");
		admin = fixtures.adminBearer(tenant);
	}

	@Test
	void seedsPermissionCatalogAndSystemRoles() {
		TenantContext.run(tenant.info(), () -> {
			assertThat(jdbcTemplate.queryForList("SELECT code FROM permission", String.class))
					.containsExactlyInAnyOrderElementsOf(Permissions.ALL);
			assertThat(permissionsOf("TENANT_ADMIN")).containsExactlyInAnyOrderElementsOf(Permissions.ALL);
			assertThat(permissionsOf("HR_ADMIN")).doesNotContain("ROLE_MANAGE", "SETTINGS_MANAGE")
					.contains("EMPLOYEE_MANAGE", "USER_MANAGE");
			assertThat(permissionsOf("MANAGER")).containsExactlyInAnyOrder("LEAVE_APPLY", "LEAVE_APPROVE",
					"EMPLOYEE_VIEW_TEAM", "REPORT_VIEW_TEAM");
			assertThat(permissionsOf("EMPLOYEE")).containsExactly("LEAVE_APPLY");
		});
	}

	@Test
	void meDescribesTheSignedInUserAndActivatesThem() throws Exception {
		assertThat(fixtures.userStatus(tenant, tenant.adminEmail())).isEqualTo("INVITED");

		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(tenant.adminEmail()))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.tenant.key").value(tenant.info().key()))
				.andExpect(jsonPath("$.roles", containsInAnyOrder("TENANT_ADMIN")))
				.andExpect(jsonPath("$.permissions", hasSize(Permissions.ALL.size())))
				.andExpect(jsonPath("$.employee").doesNotExist());
	}

	@Test
	void invitesUsersWhoGetTheirRolesPermissions() throws Exception {
		String email = TestFixtures.unique("hr") + "@example.test";
		perform(post("/api/v1/users"), admin, Map.of("email", email, "roleCodes", List.of("HR_ADMIN")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("INVITED"))
				.andExpect(jsonPath("$.idpSubject").isNotEmpty())
				.andExpect(jsonPath("$.roles", containsInAnyOrder("HR_ADMIN")));

		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearerFor(email)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.permissions", hasItem("EMPLOYEE_MANAGE")));

		perform(post("/api/v1/users"), admin, Map.of("email", email, "roleCodes", List.of("EMPLOYEE")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("USER_ALREADY_EXISTS"));
		perform(post("/api/v1/users"), admin, Map.of("email", "x-" + email, "roleCodes", List.of("NOPE")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("UNKNOWN_ROLE"));
	}

	@Test
	void nobodyGrantsPermissionsTheyLack() throws Exception {
		String hr = invite("HR_ADMIN");
		String target = invite("EMPLOYEE");
		String targetId = userId(target);

		perform(put("/api/v1/users/{id}/roles", targetId), bearerFor(hr), Map.of("roleCodes", List.of("TENANT_ADMIN")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("PRIVILEGE_ESCALATION"));
		perform(put("/api/v1/users/{id}/roles", targetId), bearerFor(hr), Map.of("roleCodes", List.of("MANAGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles", containsInAnyOrder("MANAGER")));
	}

	@Test
	void tenantKeepsAnEnabledAdmin() throws Exception {
		String adminId = userId(tenant.adminEmail());

		perform(put("/api/v1/users/{id}/roles", adminId), admin, Map.of("roleCodes", List.of("EMPLOYEE")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("LAST_TENANT_ADMIN"));
		mockMvc.perform(post("/api/v1/users/{id}/disable", adminId).header("Authorization", admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("CANNOT_DISABLE_SELF"));
	}

	@Test
	void disabledUsersAreRejectedUntilReEnabled() throws Exception {
		String email = invite("EMPLOYEE");
		String id = userId(email);
		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearerFor(email))).andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/users/{id}/disable", id).header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DISABLED"));
		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearerFor(email)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("USER_NOT_REGISTERED"));

		mockMvc.perform(post("/api/v1/users/{id}/enable", id).header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));
		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearerFor(email))).andExpect(status().isOk());

		// Cache-clearing events run after commit; they must still be marked complete in the tenant's outbox.
		TenantContext.run(tenant.info(), () -> assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM event_publication WHERE event_type LIKE '%UserAccessChanged' AND completion_date IS NULL",
				Integer.class)).isZero());
	}

	@Test
	void customRolesGrantTheirPermissions() throws Exception {
		String code = "HOLIDAY_" + TestFixtures.unique("x").substring(2, 8).toUpperCase();
		String roleId = jsonMapper.readTree(perform(post("/api/v1/roles"), admin,
				Map.of("code", code, "name", "Holiday keeper", "permissionCodes", List.of("HOLIDAY_MANAGE")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString()).get("id").asString();

		String email = invite("EMPLOYEE");
		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearerFor(email)))
				.andExpect(jsonPath("$.permissions", containsInAnyOrder("LEAVE_APPLY"))); // now cached
		perform(put("/api/v1/users/{id}/roles", userId(email)), admin, Map.of("roleCodes", List.of("EMPLOYEE", code)))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearerFor(email)))
				.andExpect(jsonPath("$.permissions", containsInAnyOrder("LEAVE_APPLY", "HOLIDAY_MANAGE")));

		mockMvc.perform(delete("/api/v1/roles/{id}", roleId).header("Authorization", admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("ROLE_IN_USE"));
		String employeeRoleId = TenantContext.call(tenant.info(),
				() -> jdbcTemplate.queryForObject("SELECT id::text FROM role WHERE code = 'EMPLOYEE'", String.class));
		perform(put("/api/v1/roles/{id}", employeeRoleId), admin,
				Map.of("name", "Changed", "permissionCodes", List.of("LEAVE_APPLY")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("SYSTEM_ROLE_READ_ONLY"));
	}

	@Test
	void userAndRoleAdministrationNeedsPermission() throws Exception {
		String employee = bearerFor(invite("EMPLOYEE"));
		mockMvc.perform(get("/api/v1/users").header("Authorization", employee))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
		mockMvc.perform(get("/api/v1/roles").header("Authorization", employee)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/permissions").header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(Permissions.ALL.size())));
	}

	private List<String> permissionsOf(String roleCode) {
		return jdbcTemplate.queryForList("""
				SELECT p.code FROM role r JOIN role_permission rp ON rp.role_id = r.id JOIN permission p ON p.id = rp.permission_id
				WHERE r.code = ?
				""", String.class, roleCode);
	}

	private String invite(String roleCode) throws Exception {
		String email = TestFixtures.unique(roleCode.toLowerCase()) + "@example.test";
		perform(post("/api/v1/users"), admin, Map.of("email", email, "roleCodes", List.of(roleCode)))
				.andExpect(status().isCreated());
		return email;
	}

	private String userId(String email) {
		return TenantContext.call(tenant.info(), () -> jdbcTemplate
				.queryForObject("SELECT id::text FROM app_user WHERE email = ?", String.class, email));
	}

	private String bearerFor(String email) {
		return fixtures.bearer(tenant, fixtures.subjectOf(tenant, email));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String bearer, Object body) throws Exception {
		return mockMvc.perform(request.header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(body)));
	}

}
