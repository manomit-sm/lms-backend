package com.bsolz.lms.leavepolicy.web;

import com.bsolz.lms.leavepolicy.service.LeavePeriodService;
import com.bsolz.lms.leavepolicy.web.dto.LeavePeriodRequest;
import com.bsolz.lms.leavepolicy.web.dto.LeavePeriodResponse;
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

/** Readable by every tenant user; created with LEAVE_POLICY_MANAGE. */
@RestController
@RequestMapping("/api/v1/leave-periods")
@RequiredArgsConstructor
class LeavePeriodController {

	private final LeavePeriodService service;

	@GetMapping
	List<LeavePeriodResponse> list() {
		return service.list();
	}

	@GetMapping("/current")
	LeavePeriodResponse current() {
		return service.current();
	}

	@GetMapping("/{leavePeriodId}")
	LeavePeriodResponse get(@PathVariable UUID leavePeriodId) {
		return service.get(leavePeriodId);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('LEAVE_POLICY_MANAGE')")
	ResponseEntity<LeavePeriodResponse> create(@Valid @RequestBody LeavePeriodRequest request) {
		LeavePeriodResponse created = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/leave-periods/" + created.id())).body(created);
	}

}
