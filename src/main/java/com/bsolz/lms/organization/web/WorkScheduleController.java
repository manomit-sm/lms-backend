package com.bsolz.lms.organization.web;

import com.bsolz.lms.organization.service.WorkScheduleService;
import com.bsolz.lms.organization.web.dto.WorkScheduleRequest;
import com.bsolz.lms.organization.web.dto.WorkScheduleResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Readable by every tenant user (for pick lists); changed with ORGANIZATION_MANAGE. */
@RestController
@RequestMapping("/api/v1/work-schedules")
@RequiredArgsConstructor
class WorkScheduleController {

	private final WorkScheduleService service;

	@GetMapping
	List<WorkScheduleResponse> list() {
		return service.list();
	}

	@GetMapping("/{workScheduleId}")
	WorkScheduleResponse get(@PathVariable UUID workScheduleId) {
		return service.get(workScheduleId);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('ORGANIZATION_MANAGE')")
	ResponseEntity<WorkScheduleResponse> create(@Valid @RequestBody WorkScheduleRequest request) {
		WorkScheduleResponse created = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/work-schedules/" + created.id())).body(created);
	}

	@PutMapping("/{workScheduleId}")
	@PreAuthorize("hasAuthority('ORGANIZATION_MANAGE')")
	WorkScheduleResponse update(@PathVariable UUID workScheduleId, @Valid @RequestBody WorkScheduleRequest request) {
		return service.update(workScheduleId, request);
	}

}
