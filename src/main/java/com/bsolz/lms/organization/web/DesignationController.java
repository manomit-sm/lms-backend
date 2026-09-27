package com.bsolz.lms.organization.web;

import com.bsolz.lms.organization.service.DesignationService;
import com.bsolz.lms.organization.web.dto.DesignationRequest;
import com.bsolz.lms.organization.web.dto.DesignationResponse;
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
@RequestMapping("/api/v1/designations")
@RequiredArgsConstructor
class DesignationController {

	private final DesignationService service;

	@GetMapping
	List<DesignationResponse> list() {
		return service.list();
	}

	@GetMapping("/{designationId}")
	DesignationResponse get(@PathVariable UUID designationId) {
		return service.get(designationId);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('ORGANIZATION_MANAGE')")
	ResponseEntity<DesignationResponse> create(@Valid @RequestBody DesignationRequest request) {
		DesignationResponse created = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/designations/" + created.id())).body(created);
	}

	@PutMapping("/{designationId}")
	@PreAuthorize("hasAuthority('ORGANIZATION_MANAGE')")
	DesignationResponse update(@PathVariable UUID designationId, @Valid @RequestBody DesignationRequest request) {
		return service.update(designationId, request);
	}

}
