package com.bsolz.lms.leave.web.dto;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Upload the file with {@code method} to {@code uploadUrl}, sending exactly {@code headers}, before
 * {@code expiresAt}; then submit the request with {@code attachmentId}.
 */
public record AttachmentUploadResponse(UUID attachmentId, URI uploadUrl, String method, Map<String, String> headers,
		Instant expiresAt) {
}
