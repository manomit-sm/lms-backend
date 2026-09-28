package com.bsolz.lms.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.holiday.api.HolidayApi;
import com.bsolz.lms.holiday.api.HolidayInfo;
import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class HolidayApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	HolidayApi holidayApi;

	@Autowired
	JsonMapper jsonMapper;

	TestTenant tenant;

	String admin;

	String blrCode;

	UUID bengaluru;

	UUID london;

	UUID engineering;

	@BeforeEach
	void setUp() throws Exception {
		tenant = fixtures.newTenant("hol");
		admin = fixtures.adminBearer(tenant);
		blrCode = "BLR" + TestFixtures.unique("").substring(1, 5).toUpperCase(Locale.ROOT);
		bengaluru = location(blrCode, "Asia/Kolkata");
		london = location("LON" + TestFixtures.unique("").substring(1, 5).toUpperCase(Locale.ROOT), "Europe/London");
		engineering = fixtures.createDepartment(tenant);
	}

	@Test
	void holidaysApplyByLocationAndDepartment() throws Exception {
		create(holiday("New Year", "2026-01-01", null, null)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("PUBLIC"));
		create(holiday("Diwali", "2026-11-09", List.of(bengaluru), null)).andExpect(status().isCreated());
		create(holiday("Engineering day", "2026-05-15", null, List.of(engineering))).andExpect(status().isCreated());
		Map<String, Object> optional = holiday("Karva Chauth", "2026-10-29", List.of(bengaluru), null);
		optional.put("type", "OPTIONAL");
		create(optional).andExpect(status().isCreated());

		EmployeeResponse inBengaluru = fixtures.createEmployee(tenant, "Anu", engineering, null, null, bengaluru,
				LocalDate.of(2024, 1, 1));
		EmployeeResponse inLondon = fixtures.createEmployee(tenant, "Liam", fixtures.createDepartment(tenant), null,
				null, london, LocalDate.of(2024, 1, 1));

		assertThat(holidaysFor(inBengaluru)).extracting(HolidayInfo::name)
				.containsExactly("New Year", "Engineering day", "Karva Chauth", "Diwali");
		assertThat(holidaysFor(inBengaluru)).filteredOn(HolidayInfo::isDayOff).extracting(HolidayInfo::name)
				.doesNotContain("Karva Chauth");
		assertThat(holidaysFor(inLondon)).extracting(HolidayInfo::name).containsExactly("New Year");

		mockMvc.perform(get("/api/v1/holidays?year=2026").header("Authorization", admin))
				.andExpect(jsonPath("$[*].name", contains("New Year", "Engineering day", "Karva Chauth", "Diwali")));
		mockMvc.perform(get("/api/v1/holidays?year=2026&locationId={id}", london).header("Authorization", admin))
				.andExpect(jsonPath("$[*].name", containsInAnyOrder("New Year", "Engineering day")));
		String liam = fixtures.bearer(tenant, fixtures.awaitSubject(tenant, inLondon.email()));
		mockMvc.perform(get("/api/v1/holidays/me?from=2026-01-01&to=2026-12-31").header("Authorization", liam))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].name", contains("New Year")));
	}

	@Test
	void managesHolidays() throws Exception {
		String id = jsonMapper.readTree(create(holiday("Founders Day", "2026-03-03", null, null))
				.andReturn().getResponse().getContentAsString()).get("id").asString();

		create(holiday("founders day", "2026-03-03", null, null)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("HOLIDAY_ALREADY_EXISTS"));
		create(holiday("Nowhere day", "2026-03-04", List.of(UUID.randomUUID()), null)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("UNKNOWN_ORG_UNIT"));

		send(put("/api/v1/holidays/{id}", id), holiday("Founders Day", "2026-03-04", List.of(london), null))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.date").value("2026-03-04"))
				.andExpect(jsonPath("$.locationIds[0]").value(london.toString()));
		mockMvc.perform(delete("/api/v1/holidays/{id}", id).header("Authorization", admin))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/holidays/{id}", id).header("Authorization", admin))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("HOLIDAY_NOT_FOUND"));

		mockMvc.perform(get("/api/v1/holidays?from=2026-02-01").header("Authorization", admin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_DATE_RANGE"));
	}

	@Test
	void importsHolidaysFromCsv() throws Exception {
		create(holiday("Republic Day", "2026-01-26", null, null)).andExpect(status().isCreated());
		String csv = """
				Date,Name,Type,Description,Locations,Departments
				2026-01-26,Republic Day,,,,
				2026-08-15,Independence Day,public,National holiday,%s,
				2026-10-02,"Gandhi Jayanti, observed",COMPANY,,,
				2026-12-24,Christmas Eve,optional,,,
				""".formatted(blrCode.toLowerCase(Locale.ROOT));

		importCsv(csv).andExpect(status().isOk())
				.andExpect(jsonPath("$.created").value(3))
				.andExpect(jsonPath("$.skipped").value(1));
		mockMvc.perform(get("/api/v1/holidays?year=2026").header("Authorization", admin))
				.andExpect(jsonPath("$[?(@.name == 'Independence Day')].locationIds[0]").value(bengaluru.toString()))
				.andExpect(jsonPath("$[?(@.name == 'Gandhi Jayanti, observed')].type").value("COMPANY"))
				.andExpect(jsonPath("$[?(@.name == 'Christmas Eve')].type").value("OPTIONAL"));
	}

	@Test
	void rejectsTheWholeImportIfAnyRowIsInvalid() throws Exception {
		String csv = """
				date,name,type,locations
				2027-01-01,New Year,,
				2027-13-01,Bad date,,
				2027-02-02,Unknown place,,NOWHERE
				2027-03-03,,HOLIDAY,
				2027-01-01,new year,,
				""";

		importCsv(csv).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_HOLIDAY_IMPORT"))
				.andExpect(jsonPath("$.errors[*].row", contains(3, 4, 5, 6)))
				.andExpect(jsonPath("$.errors[1].message").value("unknown location code NOWHERE"))
				.andExpect(jsonPath("$.errors[3].message").value("duplicates row 2"));
		mockMvc.perform(get("/api/v1/holidays?year=2027").header("Authorization", admin))
				.andExpect(jsonPath("$.length()").value(0));

		importCsv("name\nNo date column\n").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("The header row must include the columns date and name"));
	}

	@Test
	void everyoneReadsButOnlyHolidayManagersChange() throws Exception {
		String email = fixtures.createEmployee(tenant, "Reader", engineering, null).email();
		String employee = fixtures.bearer(tenant, fixtures.awaitSubject(tenant, email));
		mockMvc.perform(get("/api/v1/holidays").header("Authorization", employee)).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/holidays").header("Authorization", employee)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(holiday("Mine", "2026-04-01", null, null))))
				.andExpect(status().isForbidden());
		mockMvc.perform(multipart("/api/v1/holidays/import")
				.file(new MockMultipartFile("file", "h.csv", "text/csv", "date,name\n".getBytes(StandardCharsets.UTF_8)))
				.header("Authorization", employee))
				.andExpect(status().isForbidden());
	}

	private List<HolidayInfo> holidaysFor(EmployeeResponse employee) {
		return TenantContext.call(tenant.info(),
				() -> holidayApi.findHolidaysFor(employee.id(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
	}

	private UUID location(String code, String timezone) throws Exception {
		return UUID.fromString(jsonMapper.readTree(send(post("/api/v1/locations"),
				Map.of("code", code, "name", code, "countryCode", "IN", "timezone", timezone))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString()).get("id").asString());
	}

	private static Map<String, Object> holiday(String name, String date, List<UUID> locationIds, List<UUID> departmentIds) {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		body.put("date", date);
		body.put("locationIds", locationIds);
		body.put("departmentIds", departmentIds);
		return body;
	}

	private ResultActions importCsv(String csv) throws Exception {
		return mockMvc.perform(multipart("/api/v1/holidays/import")
				.file(new MockMultipartFile("file", "holidays.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
				.header("Authorization", admin));
	}

	private ResultActions create(Object body) throws Exception {
		return send(post("/api/v1/holidays"), body);
	}

	private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
		return mockMvc.perform(request.header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(body)));
	}

}
