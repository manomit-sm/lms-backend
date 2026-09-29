package com.bsolz.lms.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Terse API calls for tests that drive several users through a flow. */
@Component
@RequiredArgsConstructor
public class ApiClient {

	private final MockMvc mockMvc;

	private final JsonMapper jsonMapper;

	public ResultActions get(String bearer, String path, Object... variables) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.get(path, variables).header("Authorization", bearer));
	}

	public ResultActions post(String bearer, String path, Object body, Object... variables) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post(path, variables)
				.header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(body == null ? Map.of() : body)));
	}

	public ResultActions put(String bearer, String path, Object body, Object... variables) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.put(path, variables)
				.header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(body)));
	}

	public JsonNode json(ResultActions result) throws Exception {
		return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	/** Submits a full-day request and returns the created request; fails unless it was created. */
	public JsonNode submitLeave(String bearer, UUID leaveTypeId, LocalDate start, LocalDate end) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("leaveTypeId", leaveTypeId);
		body.put("startDate", start.toString());
		body.put("endDate", end.toString());
		return submitLeave(bearer, body);
	}

	public JsonNode submitLeave(String bearer, Map<String, Object> body) throws Exception {
		return json(post(bearer, "/api/v1/leave-requests", body).andExpect(status().isCreated()));
	}

	/** Approves the oldest task waiting for the user; returns the approval. */
	public JsonNode approveFirstTask(String bearer) throws Exception {
		JsonNode tasks = json(get(bearer, "/api/v1/approvals/tasks"));
		return json(post(bearer, "/api/v1/approvals/tasks/{id}/approve", null, tasks.get(0).get("taskId").asString())
				.andExpect(status().isOk()));
	}

	/** A Monday at least two weeks ahead whose working week falls in the same year. */
	public static LocalDate mondayAhead(LocalDate today) {
		LocalDate day = today.plusDays(14);
		while (day.getDayOfWeek() != DayOfWeek.MONDAY || day.plusDays(6).getYear() != day.getYear()) {
			day = day.plusDays(1);
		}
		return day;
	}

}
