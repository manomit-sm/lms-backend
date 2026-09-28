package com.bsolz.lms.leavepolicy.web;

import com.bsolz.lms.leavepolicy.service.LeaveTypeService;
import com.bsolz.lms.leavepolicy.web.dto.LeaveTypeRequest;
import com.bsolz.lms.leavepolicy.web.dto.LeaveTypeResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Readable by every tenant user; changed with LEAVE_POLICY_MANAGE. Deactivate instead of deleting. */
@RestController
@RequestMapping("/api/v1/leave-types")
@RequiredArgsConstructor
class LeaveTypeController {

	private final LeaveTypeService service;

	@GetMapping
	List<LeaveTypeResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
		return service.list(includeInactive);
	}

	@GetMapping("/{leaveTypeId}")
	LeaveTypeResponse get(@PathVariable UUID leaveTypeId) {
		return service.get(leaveTypeId);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('LEAVE_POLICY_MANAGE')")
	ResponseEntity<LeaveTypeResponse> create(@Valid @RequestBody LeaveTypeRequest request) {
		LeaveTypeResponse created = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/leave-types/" + created.id())).body(created);
	}

	@PutMapping("/{leaveTypeId}")
	@PreAuthorize("hasAuthority('LEAVE_POLICY_MANAGE')")
	LeaveTypeResponse update(@PathVariable UUID leaveTypeId, @Valid @RequestBody LeaveTypeRequest request) {
		return service.update(leaveTypeId, request);
	}

}
