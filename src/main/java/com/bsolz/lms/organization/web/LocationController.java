package com.bsolz.lms.organization.web;

import com.bsolz.lms.organization.service.LocationService;
import com.bsolz.lms.organization.web.dto.LocationRequest;
import com.bsolz.lms.organization.web.dto.LocationResponse;
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
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
class LocationController {

	private final LocationService service;

	@GetMapping
	List<LocationResponse> list() {
		return service.list();
	}

	@GetMapping("/{locationId}")
	LocationResponse get(@PathVariable UUID locationId) {
		return service.get(locationId);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('ORGANIZATION_MANAGE')")
	ResponseEntity<LocationResponse> create(@Valid @RequestBody LocationRequest request) {
		LocationResponse created = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/locations/" + created.id())).body(created);
	}

	@PutMapping("/{locationId}")
	@PreAuthorize("hasAuthority('ORGANIZATION_MANAGE')")
	LocationResponse update(@PathVariable UUID locationId, @Valid @RequestBody LocationRequest request) {
		return service.update(locationId, request);
	}

}
