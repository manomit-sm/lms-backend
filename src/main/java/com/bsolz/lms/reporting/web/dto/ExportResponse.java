package com.bsolz.lms.reporting.web.dto;

import com.bsolz.lms.reporting.model.enums.ExportFormat;
import com.bsolz.lms.reporting.model.enums.ExportStatus;
import com.bsolz.lms.reporting.model.enums.ReportType;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * An export. Poll it until it is {@code COMPLETED} (or {@code FAILED}).
 *
 * @param downloadUrl a short-lived URL, when completed; fetch the export again for a fresh one
 * @param downloadUrlExpiresAt when {@code downloadUrl} stops working
 */
public record ExportResponse(UUID id, ReportType report, ExportFormat format, ExportStatus status, String fileName,
		Integer rowCount, Long sizeBytes, String error, Instant createdAt, Instant completedAt, URI downloadUrl,
		Instant downloadUrlExpiresAt) {
}
