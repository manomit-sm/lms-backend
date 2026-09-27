package com.bsolz.lms.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.platform.service.TenantProvisioningService;
import com.bsolz.lms.platform.service.TenantRegistryService;
import com.bsolz.lms.shared.security.local.LocalTokenIssuer;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The /api/** pipeline: token validation, tenant resolution and binding. */
@IntegrationTest
class TenantRequestSecurityTests {

	private static final Duration TTL = Duration.ofMinutes(5);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	LocalTokenIssuer tokens;

	@Autowired
	SecurityProperties securityProperties;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	TenantProvisioningService provisioning;

	@Autowired
	TenantRegistryService tenantRegistry;

	@Autowired
	JdbcTemplate jdbcTemplate;

	TestTenant tenant;

	String admin;

	@BeforeEach
	void provisionTenant() {
		tenant = fixtures.newTenant("sec");
		admin = tenant.adminSubject();
	}

	@Test
	void bindsTenantAndSchemaForTheRequest() throws Exception {
		whoami(tokens.issueTenantToken(admin, tenant.id(), TTL))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantKey").value(tenant.info().key()))
				.andExpect(jsonPath("$.subject").value(admin))
				.andExpect(jsonPath("$.schema").value(tenant.info().schemaName()))
				.andExpect(jsonPath("$.databaseSchema").value(tenant.info().schemaName()));
	}

	@Test
	void rejectsMissingOrInvalidTokens() throws Exception {
		mockMvc.perform(get("/api/test/whoami"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().exists("WWW-Authenticate"))
				.andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

		whoami("not-a-jwt").andExpect(status().isUnauthorized());
		Instant tenMinutesAgo = Instant.now().minus(Duration.ofMinutes(10));
		String expired = tokens.issue(securityProperties.tenant(), admin, Map.of(LmsClaims.TENANT_ID,
				tenant.id().toString(), "iat", tenMinutesAgo, "exp", tenMinutesAgo.plus(Duration.ofMinutes(5))), TTL);
		whoami(expired).andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsTokensThatAreNotTenantAccessTokens() throws Exception {
		var realm = securityProperties.tenant();
		// no tenant_id claim
		whoami(tokens.issue(realm, "alice", Map.of(), TTL)).andExpect(status().isUnauthorized());
		// tenant_id not a UUID
		whoami(tokens.issue(realm, "alice", Map.of(LmsClaims.TENANT_ID, "acme"), TTL))
				.andExpect(status().isUnauthorized());
		// an ID token, not an access token
		whoami(tokens.issue(realm, "alice", Map.of(LmsClaims.TENANT_ID, tenant.id().toString(), LmsClaims.TOKEN_USE, "id"), TTL))
				.andExpect(status().isUnauthorized());
		// issued to an unknown app client
		whoami(tokens.issue(realm, "alice", Map.of(LmsClaims.TENANT_ID, tenant.id().toString(), LmsClaims.CLIENT_ID, "other-app"), TTL))
				.andExpect(status().isUnauthorized());
		// a platform-admin token
		whoami(tokens.issuePlatformToken("admin", true, TTL)).andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsUnknownTenant() throws Exception {
		whoami(tokens.issueTenantToken("alice", UUID.randomUUID(), TTL))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("TENANT_NOT_FOUND"));
	}

	@Test
	void rejectsSuspendedTenant() throws Exception {
		provisioning.suspend(tenant.id());

		whoami(tokens.issueTenantToken(admin, tenant.id(), TTL))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("TENANT_SUSPENDED"));
	}

	@Test
	void rejectsTenantWhoseSchemaMigrationFailed() throws Exception {
		jdbcTemplate.update("UPDATE public.tenant SET migration_state = 'FAILED' WHERE id = ?", tenant.id());
		tenantRegistry.evict(tenant.id());

		whoami(tokens.issueTenantToken(admin, tenant.id(), TTL))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errorCode").value("TENANT_UNAVAILABLE"));
	}

	@Test
	void rejectsSubjectWithoutUserInTheTenant() throws Exception {
		whoami(tokens.issueTenantToken("someone-else", tenant.id(), TTL))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("USER_NOT_REGISTERED"));
	}

	@Test
	void unknownApiPathIsNotFoundOnceAuthenticated() throws Exception {
		mockMvc.perform(get("/api/v1/does-not-exist")
				.header("Authorization", "Bearer " + tokens.issueTenantToken(admin, tenant.id(), TTL)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
	}

	@Test
	void pathsOutsideTheApiAreDenied() throws Exception {
		mockMvc.perform(get("/internal/anything")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	private ResultActions whoami(String token) throws Exception {
		return mockMvc.perform(get("/api/test/whoami").header("Authorization", "Bearer " + token));
	}

}
