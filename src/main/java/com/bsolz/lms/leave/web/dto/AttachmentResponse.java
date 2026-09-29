package com.bsolz.lms.leave.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(UUID id, String fileName, String contentType, long sizeBytes, Instant createdAt) {
}
