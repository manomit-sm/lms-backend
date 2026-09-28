package com.bsolz.lms.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.settings.api.SettingsApi;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class SettingsApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	SettingsApi settingsApi;

	@Autowired
	JsonMapper jsonMapper;

	TestTenant tenant;

	String admin;

	@BeforeEach
	void setUp() {
		tenant = fixtures.newTenant("set");
		admin = fixtures.adminBearer(tenant);
	}

	@Test
	void defaultsFollowTheTenantUntilChanged() throws Exception {
		mockMvc.perform(get("/api/v1/settings").header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.leaveYearStartMonth").value(1))
				.andExpect(jsonPath("$.timezone").doesNotExist())
				.andExpect(jsonPath("$.effectiveTimezone").value("Europe/Brussels"))
				.andExpect(jsonPath("$.dateFormat").value("dd/MM/yyyy"))
				.andExpect(jsonPath("$.weekStartDay").value("MONDAY"));

		update(settings(4, "Asia/Kolkata", "yyyy-MM-dd")).andExpect(status().isOk())
				.andExpect(jsonPath("$.effectiveTimezone").value("Asia/Kolkata"))
				.andExpect(jsonPath("$.organizationName").value("Acme"));

		TenantContext.run(tenant.info(), () -> {
			assertThat(settingsApi.current().leaveYearStartMonth()).isEqualTo(4);
			assertThat(settingsApi.current().timezone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
			assertThat(settingsApi.current().weekStartDay()).isEqualTo(DayOfWeek.SUNDAY);
		});
	}

	@Test
	void validatesSettings() throws Exception {
		update(settings(13, null, "dd/MM/yyyy")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("leaveYearStartMonth"));
		update(settings(1, "Mars/Base", "dd/MM/yyyy")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_TIMEZONE"));
		update(settings(1, null, "yyyy/dd")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_DATE_FORMAT"));
	}

	@Test
	void onlySettingsManagersChangeSettings() throws Exception {
		String email = fixtures.createEmployee(tenant, "Reader", fixtures.createDepartment(tenant), null).email();
		String employee = fixtures.bearer(tenant, fixtures.awaitSubject(tenant, email));
		mockMvc.perform(get("/api/v1/settings").header("Authorization", employee)).andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/settings").header("Authorization", employee)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(settings(1, null, "dd/MM/yyyy"))))
				.andExpect(status().isForbidden());

		// HR admins can do most things, but not change settings.
		fixtures.grantRoles(tenant, email, "HR_ADMIN");
		mockMvc.perform(put("/api/v1/settings").header("Authorization", employee)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(settings(1, null, "dd/MM/yyyy"))))
				.andExpect(status().isForbidden());
	}

	private ResultActions update(Map<String, Object> body) throws Exception {
		return mockMvc.perform(put("/api/v1/settings").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(body)));
	}

	private static Map<String, Object> settings(int month, String timezone, String dateFormat) {
		Map<String, Object> body = new HashMap<>();
		body.put("leaveYearStartMonth", month);
		body.put("timezone", timezone);
		body.put("dateFormat", dateFormat);
		body.put("weekStartDay", "SUNDAY");
		body.put("organizationName", "Acme");
		return body;
	}

}
