package com.bsolz.lms.organization.web;

import com.bsolz.lms.organization.service.DepartmentService;
import com.bsolz.lms.organization.web.dto.DepartmentRequest;
import com.bsolz.lms.organization.web.dto.DepartmentResponse;
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
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
class DepartmentController {

	private final DepartmentService service;

	@GetMapping
	List<DepartmentResponse> list() {
		return service.list();
	}

	@GetMapping("/{departmentId}")
	DepartmentResponse get(@PathVariable UUID departmentId) {
		return service.get(departmentId);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('ORGANIZATION_MANAGE')")
	ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
		DepartmentResponse created = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/departments/" + created.id())).body(created);
	}

	@PutMapping("/{departmentId}")
	@PreAuthorize("hasAuthority('ORGANIZATION_MANAGE')")
	DepartmentResponse update(@PathVariable UUID departmentId, @Valid @RequestBody DepartmentRequest request) {
		return service.update(departmentId, request);
	}

}
