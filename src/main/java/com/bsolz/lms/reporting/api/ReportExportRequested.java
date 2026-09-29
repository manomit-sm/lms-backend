package com.bsolz.lms.reporting.api;

import java.util.UUID;

/** Published when a report export is requested; the export is generated after the request commits. */
public record ReportExportRequested(UUID tenantId, UUID exportId) {
}
