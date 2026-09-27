package com.bsolz.lms.identity.web;

import com.bsolz.lms.identity.service.RoleService;
import com.bsolz.lms.identity.web.dto.CreateRoleRequest;
import com.bsolz.lms.identity.web.dto.PermissionResponse;
import com.bsolz.lms.identity.web.dto.RoleResponse;
import com.bsolz.lms.identity.web.dto.UpdateRoleRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Roles are readable by user and role managers (to assign them); editable with ROLE_MANAGE. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class RoleController {

	private final RoleService roleService;

	@GetMapping("/roles")
	@PreAuthorize("hasAnyAuthority('USER_MANAGE', 'ROLE_MANAGE')")
	List<RoleResponse> list() {
		return roleService.list();
	}

	@GetMapping("/roles/{roleId}")
	@PreAuthorize("hasAnyAuthority('USER_MANAGE', 'ROLE_MANAGE')")
	RoleResponse get(@PathVariable UUID roleId) {
		return roleService.get(roleId);
	}

	@GetMapping("/permissions")
	@PreAuthorize("hasAnyAuthority('USER_MANAGE', 'ROLE_MANAGE')")
	List<PermissionResponse> permissions() {
		return roleService.permissions();
	}

	@PostMapping("/roles")
	@PreAuthorize("hasAuthority('ROLE_MANAGE')")
	ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
		RoleResponse created = roleService.create(request);
		return ResponseEntity.created(URI.create("/api/v1/roles/" + created.id())).body(created);
	}

	@PutMapping("/roles/{roleId}")
	@PreAuthorize("hasAuthority('ROLE_MANAGE')")
	RoleResponse update(@PathVariable UUID roleId, @Valid @RequestBody UpdateRoleRequest request) {
		return roleService.update(roleId, request);
	}

	@DeleteMapping("/roles/{roleId}")
	@PreAuthorize("hasAuthority('ROLE_MANAGE')")
	ResponseEntity<Void> delete(@PathVariable UUID roleId) {
		roleService.delete(roleId);
		return ResponseEntity.noContent().build();
	}

}
