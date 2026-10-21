package com.bsolz.lms.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bsolz.lms.support.IntegrationTest;
import com.bsolz.lms.support.TestFixtures;
import com.bsolz.lms.support.TestTenant;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The local profile's stand-in for S3: browsers upload to and download from the presigned URLs. */
@IntegrationTest
class LocalStorageTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestFixtures fixtures;

	@Autowired
	JsonMapper jsonMapper;

	@Autowired
	InMemoryFileStorage storage;

	@Test
	void uploadsToAPresignedUrlAndDownloadsTheFile() throws Exception {
		TestTenant tenant = fixtures.newTenant("sto");
		byte[] pdf = "%PDF-1.7 certificate".getBytes();
		JsonNode upload = jsonMapper.readTree(mockMvc.perform(post("/api/v1/leave-requests/attachments")
				.header("Authorization", fixtures.adminBearer(tenant))
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(
						Map.of("fileName", "certificate.pdf", "contentType", "application/pdf", "sizeBytes", pdf.length))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
		String uploadUrl = upload.get("uploadUrl").asString();
		assertThat(uploadUrl).startsWith("/local/storage/tenants/" + tenant.id() + "/");
		assertThat(upload.get("headers").get("Content-Type").asString()).isEqualTo("application/pdf");

		mockMvc.perform(put(uploadUrl).contentType(MediaType.APPLICATION_PDF).content(pdf)).andExpect(status().isOk());

		String key = uploadUrl.substring("/local/storage/".length());
		URI downloadUrl = storage.presignDownload(key, "Medical certificate.pdf").url();
		mockMvc.perform(get(downloadUrl))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_PDF))
				.andExpect(content().bytes(pdf))
				.andExpect(header().string("Content-Disposition",
						containsString("filename*=UTF-8''Medical%20certificate.pdf")));
	}

	@Test
	void acceptsOnlyPresignedKeysAndSizes() throws Exception {
		storage.presignUpload("tenants/t1/attachments/small", "image/png", 3);

		mockMvc.perform(put("/local/storage/tenants/t1/attachments/small").content(new byte[4]))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
		mockMvc.perform(put("/local/storage/tenants/t1/attachments/unknown").content(new byte[1]))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/local/storage/tenants/t1/attachments/small"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
	}

}
