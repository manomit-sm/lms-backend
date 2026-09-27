package com.bsolz.lms.organization.web;

import com.bsolz.lms.organization.model.enums.EmploymentStatus;
import com.bsolz.lms.organization.service.EmployeeFilter;
import com.bsolz.lms.organization.service.EmployeeService;
import com.bsolz.lms.organization.web.dto.EmployeeRequest;
import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.organization.web.dto.ExitEmployeeRequest;
import com.bsolz.lms.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
 * Employees. Reads are limited to the caller's data scope (self / reporting line / everyone);
 * changes need EMPLOYEE_MANAGE.
 */
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
class EmployeeController {

	private final EmployeeService employeeService;

	@GetMapping
	PageResponse<EmployeeResponse> list(@RequestParam(required = false) String search,
			@RequestParam(required = false) UUID departmentId, @RequestParam(required = false) UUID reportingManagerId,
			@RequestParam(required = false) EmploymentStatus status,
			@PageableDefault(size = 20, sort = { "firstName", "lastName" }) Pageable pageable) {
		EmployeeFilter filter = new EmployeeFilter(search, departmentId, reportingManagerId, status);
		return PageResponse.from(employeeService.list(filter, pageable));
	}

	@GetMapping("/me")
	EmployeeResponse me() {
		return employeeService.me();
	}

	@GetMapping("/me/team")
	List<EmployeeResponse> myTeam(@RequestParam(defaultValue = "false") boolean includeIndirect) {
		return employeeService.myTeam(includeIndirect);
	}

	@GetMapping("/{employeeId}")
	@PreAuthorize("@employeeAccess.canView(#employeeId)")
	EmployeeResponse get(@PathVariable UUID employeeId) {
		return employeeService.get(employeeId);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
	ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
		EmployeeResponse created = employeeService.create(request);
		return ResponseEntity.created(URI.create("/api/v1/employees/" + created.id())).body(created);
	}

	@PutMapping("/{employeeId}")
	@PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
	EmployeeResponse update(@PathVariable UUID employeeId, @Valid @RequestBody EmployeeRequest request) {
		return employeeService.update(employeeId, request);
	}

	@PostMapping("/{employeeId}/exit")
	@PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
	EmployeeResponse exit(@PathVariable UUID employeeId, @Valid @RequestBody ExitEmployeeRequest request) {
		return employeeService.exit(employeeId, request);
	}

}
