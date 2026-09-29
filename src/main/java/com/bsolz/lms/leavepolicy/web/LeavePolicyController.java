package com.bsolz.lms.leavepolicy.web;

import com.bsolz.lms.leavepolicy.service.LeavePolicyService;
import com.bsolz.lms.leavepolicy.web.dto.EffectivePolicyResponse;
import com.bsolz.lms.leavepolicy.web.dto.LeavePolicyRequest;
import com.bsolz.lms.leavepolicy.web.dto.LeavePolicyResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
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

/**
 * Leave policies: readable by every tenant user (employees may read the rules), changed with
 * LEAVE_POLICY_MANAGE. {@code /effective} shows which policy governs an employee the caller can see.
 */
@RestController
@RequestMapping("/api/v1/leave-policies")
@RequiredArgsConstructor
class LeavePolicyController {

	private final LeavePolicyService service;

	@GetMapping
	List<LeavePolicyResponse> list(@RequestParam(required = false) UUID leaveTypeId) {
		return service.list(leaveTypeId);
	}

	/** Defaults: the current user, every active leave type, today. */
	@GetMapping("/effective")
	List<EffectivePolicyResponse> effective(@RequestParam(required = false) UUID employeeId,
			@RequestParam(required = false) UUID leaveTypeId, @RequestParam(required = false) LocalDate date) {
		return service.effective(employeeId, leaveTypeId, date);
	}

	@GetMapping("/{policyId}")
	LeavePolicyResponse get(@PathVariable UUID policyId) {
		return service.get(policyId);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('LEAVE_POLICY_MANAGE')")
	ResponseEntity<LeavePolicyResponse> create(@Valid @RequestBody LeavePolicyRequest request) {
		LeavePolicyResponse created = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/leave-policies/" + created.id())).body(created);
	}

	@PutMapping("/{policyId}")
	@PreAuthorize("hasAuthority('LEAVE_POLICY_MANAGE')")
	LeavePolicyResponse update(@PathVariable UUID policyId, @Valid @RequestBody LeavePolicyRequest request) {
		return service.update(policyId, request);
	}

}
