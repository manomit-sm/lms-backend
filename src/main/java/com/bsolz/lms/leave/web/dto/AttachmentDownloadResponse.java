package com.bsolz.lms.leave.web.dto;

import java.net.URI;
import java.time.Instant;

public record AttachmentDownloadResponse(URI url, Instant expiresAt) {
}
