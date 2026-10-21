package com.bsolz.lms.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class LocalDirectoryTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	JsonMapper jsonMapper;

	TestTenant tenant;

	@BeforeEach
	void setUp() {
		tenant = fixtures.newTenant("dir");
	}

	@Test
	void listsServingTenantsWithoutAToken() throws Exception {
		mockMvc.perform(get("/local/directory/tenants"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.key == '%s')].id", tenant.info().key()).value(hasItem(tenant.id().toString())));
	}

	@Test
	void listsUsersThatCanSignInAndTheirSubjectsWork() throws Exception {
		mockMvc.perform(get("/local/directory/tenants/{id}/users", tenant.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].email").value(contains(tenant.adminEmail())))
				.andExpect(jsonPath("$[0].subject").value(tenant.adminSubject()))
				.andExpect(jsonPath("$[0].roles").value(hasItem("TENANT_ADMIN")));

		String body = mockMvc.perform(post("/local/tokens/tenant").contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(
						Map.of("subject", tenant.adminSubject(), "tenantId", tenant.id(), "ttlMinutes", 5))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		mockMvc.perform(get("/api/v1/auth/me")
				.header("Authorization", "Bearer " + jsonMapper.readTree(body).get("accessToken").asString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(tenant.adminEmail()));
	}

	@Test
	void leavesOutDisabledUsers() throws Exception {
		String email = fixtures.createEmployee(tenant, "Dora", fixtures.createDepartment(tenant), null).email();
		fixtures.awaitSubject(tenant, email);
		assertThat(emails()).containsExactlyInAnyOrder(tenant.adminEmail(), email);

		UUID userId = UUID.fromString(users().stream()
				.filter(user -> user.get("email").asString().equals(email))
				.findFirst().orElseThrow().get("id").asString());
		mockMvc.perform(post("/api/v1/users/{id}/disable", userId).header("Authorization", fixtures.adminBearer(tenant)))
				.andExpect(status().isOk());

		assertThat(emails()).containsExactly(tenant.adminEmail());
	}

	@Test
	void unknownTenantIsNotFound() throws Exception {
		mockMvc.perform(get("/local/directory/tenants/{id}/users", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
	}

	private List<String> emails() throws Exception {
		return users().stream().map(user -> user.get("email").asString()).toList();
	}

	private List<JsonNode> users() throws Exception {
		String body = mockMvc.perform(get("/local/directory/tenants/{id}/users", tenant.id()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<JsonNode> users = new ArrayList<>();
		jsonMapper.readTree(body).forEach(users::add);
		return users;
	}

}
