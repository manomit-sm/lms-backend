package com.bsolz.lms.reporting.web;

import com.bsolz.lms.reporting.service.ReportExportService;
import com.bsolz.lms.reporting.web.dto.ExportRequest;
import com.bsolz.lms.reporting.web.dto.ExportResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Report exports as CSV or XLSX (REPORT_EXPORT, plus a report view permission for the scope). Requesting
 * one returns 202 with the pending export; poll {@code GET /{id}} until it is COMPLETED, then download
 * from its {@code downloadUrl}.
 */
@RestController
@RequestMapping("/api/v1/reports/exports")
@PreAuthorize("hasAuthority('REPORT_EXPORT') and hasAnyAuthority('REPORT_VIEW_TEAM', 'REPORT_VIEW_ALL')")
@RequiredArgsConstructor
class ReportExportController {

	private final ReportExportService service;

	@PostMapping
	ResponseEntity<ExportResponse> request(@Valid @RequestBody ExportRequest request) {
		ExportResponse export = service.request(request);
		return ResponseEntity.accepted().location(URI.create("/api/v1/reports/exports/" + export.id())).body(export);
	}

	/** The caller's latest 20 exports, newest first. */
	@GetMapping
	List<ExportResponse> mine() {
		return service.mine();
	}

	@GetMapping("/{exportId}")
	ExportResponse get(@PathVariable UUID exportId) {
		return service.get(exportId);
	}

}
