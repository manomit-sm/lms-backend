package com.bsolz.lms.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.identity.idp.FakeIdentityProvider;
import com.bsolz.lms.platform.service.TenantProvisioningService;
import com.bsolz.lms.shared.security.local.LocalTokenIssuer;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class PlatformTenantApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	LocalTokenIssuer tokens;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	TenantProvisioningService provisioning;

	@Autowired
	FakeIdentityProvider identityProvider;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	JsonMapper jsonMapper;

	@Test
	void provisionsTenantWithMigratedSchemaAndInvitedAdmin() throws Exception {
		String key = TestFixtures.unique("acme");
		String schema = "tenant_" + key.replace('-', '_');
		String adminEmail = "Owner@" + key + ".test";

		mockMvc.perform(post("/platform/tenants").header("Authorization", fixtures.platformAdminBearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody(key, null, "Europe/Brussels", adminEmail)))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.key").value(key))
				.andExpect(jsonPath("$.schemaName").value(schema))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.migrationState").value("UP_TO_DATE"));

		assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name IN ('event_publication', 'employee', 'app_user')",
				Integer.class, schema)).isEqualTo(3);
		assertThat(jdbcTemplate.queryForList("""
				SELECT r.code FROM %s.app_user u JOIN %s.user_role ur ON ur.user_id = u.id JOIN %s.role r ON r.id = ur.role_id
				WHERE u.email = ? AND u.status = 'INVITED' AND u.idp_subject IS NOT NULL
				""".formatted(schema, schema, schema), String.class, adminEmail.toLowerCase()))
				.containsExactly("TENANT_ADMIN");
		assertThat(identityProvider.find(adminEmail)).isPresent();
	}

	@Test
	void addsFurtherTenantAdmins() throws Exception {
		TestTenant tenant = fixtures.newTenant("admins");
		String second = "second-admin@" + tenant.info().key() + ".test";

		mockMvc.perform(post("/platform/tenants/{id}/admins", tenant.id())
				.header("Authorization", fixtures.platformAdminBearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(Map.of("email", second))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").exists());

		assertThat(fixtures.subjectOf(tenant, second)).isNotNull();
	}

	@Test
	void anEmailBelongsToOneTenantOnly() {
		TestTenant first = fixtures.newTenant("one");
		TestTenant second = fixtures.newTenant("two");

		assertThatThrownBy(() -> provisioning.addAdmin(second.id(), first.adminEmail()))
				.hasMessageContaining("already registered with another organisation");
		assertThat(TenantContext.call(second.info(), () -> jdbcTemplate.queryForList(
				"SELECT email FROM app_user WHERE email = ?", String.class, first.adminEmail()))).isEmpty();
	}

	@Test
	void rejectsDuplicateKeyAndSubdomain() throws Exception {
		String key = TestFixtures.unique("dup");
		String subdomain = TestFixtures.unique("sub");
		create(createBody(key, subdomain, "UTC", "a@" + key + ".test")).andExpect(status().isCreated());

		create(createBody(key, null, "UTC", "b@" + key + ".test"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("TENANT_KEY_TAKEN"));

		String otherKey = TestFixtures.unique("dup");
		create(createBody(otherKey, subdomain, "UTC", "c@" + otherKey + ".test"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("SUBDOMAIN_TAKEN"));
	}

	@Test
	void validatesRequest() throws Exception {
		create(createBody("Bad Key!", null, "UTC", "x@example.test"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("key"));

		create(createBody(TestFixtures.unique("mail"), null, "UTC", "not-an-email"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("adminEmail"));

		create(createBody(TestFixtures.unique("tz"), null, "Mars/Olympus", "x@example.test"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_TIMEZONE"));
	}

	@Test
	void suspendsAndReactivates() throws Exception {
		UUID id = fixtures.newTenant("life").id();

		mockMvc.perform(post("/platform/tenants/{id}/suspend", id).header("Authorization", fixtures.platformAdminBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUSPENDED"));
		mockMvc.perform(post("/platform/tenants/{id}/suspend", id).header("Authorization", fixtures.platformAdminBearer()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("INVALID_TENANT_STATE"));
		mockMvc.perform(post("/platform/tenants/{id}/activate", id).header("Authorization", fixtures.platformAdminBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void getsAndListsTenants() throws Exception {
		String id = createdId(create(createBody(TestFixtures.unique("list"), null, "UTC", "l@example.test"))
				.andExpect(status().isCreated())
				.andReturn());

		mockMvc.perform(get("/platform/tenants/{id}", id).header("Authorization", fixtures.platformAdminBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id));
		mockMvc.perform(get("/platform/tenants/{id}", UUID.randomUUID())
				.header("Authorization", fixtures.platformAdminBearer()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("TENANT_NOT_FOUND"));
		mockMvc.perform(get("/platform/tenants?size=5").header("Authorization", fixtures.platformAdminBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(5))
				.andExpect(jsonPath("$.content").isArray());
	}

	@Test
	void requiresPlatformAdminToken() throws Exception {
		mockMvc.perform(get("/platform/tenants"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

		String notAdmin = "Bearer " + tokens.issuePlatformToken("someone", false, Duration.ofMinutes(5));
		mockMvc.perform(get("/platform/tenants").header("Authorization", notAdmin))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

		// A tenant user's token is signed for a different issuer and carries tenant_id: never valid here.
		String tenantToken = "Bearer " + tokens.issueTenantToken("user", UUID.randomUUID(), Duration.ofMinutes(5));
		mockMvc.perform(get("/platform/tenants").header("Authorization", tenantToken))
				.andExpect(status().isUnauthorized());
	}

	private ResultActions create(String body) throws Exception {
		return mockMvc.perform(post("/platform/tenants").header("Authorization", fixtures.platformAdminBearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private String createdId(MvcResult result) throws Exception {
		return jsonMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
	}

	private String createBody(String key, String subdomain, String timezone, String adminEmail) {
		Map<String, Object> body = new HashMap<>();
		body.put("key", key);
		body.put("name", "Tenant " + key);
		body.put("subdomain", subdomain);
		body.put("defaultTimezone", timezone);
		body.put("adminEmail", adminEmail);
		return jsonMapper.writeValueAsString(body);
	}

}
