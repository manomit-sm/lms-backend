package com.bsolz.lms.shared.storage;

import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.exception.CommonErrorCode;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local development only: stands in for S3 behind the in-memory storage's presigned URLs, so a browser
 * can upload attachments and download files. Open like the rest of {@code /local/**}; an upload is
 * accepted only for a key the API presigned.
 */
@RestController
@RequestMapping("/local/storage")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lms.storage.provider", havingValue = "fake")
@ConditionalOnBooleanProperty("lms.security.local-issuer.enabled")
class LocalStorageController {

	private final InMemoryFileStorage storage;

	@PutMapping("/{*key}")
	ResponseEntity<Void> upload(@PathVariable String key, @RequestBody byte[] content) {
		if (!storage.upload(key.substring(1), content)) {
			throw new ApiException(CommonErrorCode.BAD_REQUEST,
					"No upload was presigned for this URL, or the file is larger than presigned");
		}
		return ResponseEntity.ok().build();
	}

	@GetMapping("/{*key}")
	ResponseEntity<byte[]> download(@PathVariable String key, @RequestParam(required = false) String fileName) {
		InMemoryFileStorage.StoredFile file = storage.file(key.substring(1))
				.orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "File not found"));
		ResponseEntity.BodyBuilder response = ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.contentType()));
		if (fileName != null) {
			response.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString());
		}
		return response.body(file.content());
	}

}
