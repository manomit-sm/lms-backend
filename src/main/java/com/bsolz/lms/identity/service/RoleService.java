package com.bsolz.lms.identity.service;

import com.bsolz.lms.identity.entity.Permission;
import com.bsolz.lms.identity.entity.Role;
import com.bsolz.lms.identity.exception.IdentityErrorCode;
import com.bsolz.lms.identity.mapper.IdentityMapper;
import com.bsolz.lms.identity.repository.AppUserRepository;
import com.bsolz.lms.identity.repository.PermissionRepository;
import com.bsolz.lms.identity.repository.RoleRepository;
import com.bsolz.lms.identity.web.dto.CreateRoleRequest;
import com.bsolz.lms.identity.web.dto.PermissionResponse;
import com.bsolz.lms.identity.web.dto.RoleResponse;
import com.bsolz.lms.identity.web.dto.UpdateRoleRequest;
import com.bsolz.lms.shared.exception.ApiException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Custom roles. System roles are read-only; permissions come from the seeded catalog. */
@Service
@Transactional
@RequiredArgsConstructor
public class RoleService {

	private final RoleRepository roleRepository;

	private final PermissionRepository permissionRepository;

	private final AppUserRepository userRepository;

	private final IdentityMapper mapper;

	private final ApplicationEventPublisher events;

	@Transactional(readOnly = true)
	public List<RoleResponse> list() {
		return roleRepository.findAllByOrderBySystemRoleDescNameAsc().stream().map(mapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public RoleResponse get(UUID roleId) {
		return mapper.toResponse(require(roleId));
	}

	@Transactional(readOnly = true)
	public List<PermissionResponse> permissions() {
		return permissionRepository.findAllByOrderByModuleAscCodeAsc().stream().map(mapper::toResponse).toList();
	}

	public RoleResponse create(CreateRoleRequest request) {
		if (roleRepository.existsByCode(request.code())) {
			throw new ApiException(IdentityErrorCode.ROLE_CODE_TAKEN, "Role code '" + request.code() + "' is taken");
		}
		List<Permission> permissions = requirePermissions(request.permissionCodes());
		GrantGuard.requireCanGrantPermissions(permissions);
		Role role = new Role();
		role.setCode(request.code());
		role.setName(request.name().trim());
		role.setDescription(request.description());
		role.replacePermissions(permissions);
		return mapper.toResponse(roleRepository.save(role));
	}

	public RoleResponse update(UUID roleId, UpdateRoleRequest request) {
		Role role = requireCustom(roleId);
		List<Permission> permissions = requirePermissions(request.permissionCodes());
		GrantGuard.requireCanGrantPermissions(permissions);
		role.setName(request.name().trim());
		role.setDescription(request.description());
		role.replacePermissions(permissions);
		events.publishEvent(UserAccessChanged.inCurrentTenant());
		return mapper.toResponse(role);
	}

	public void delete(UUID roleId) {
		Role role = requireCustom(roleId);
		if (userRepository.existsByRolesId(roleId)) {
			throw new ApiException(IdentityErrorCode.ROLE_IN_USE, "Remove this role from all users before deleting it");
		}
		roleRepository.delete(role);
	}

	private Role requireCustom(UUID roleId) {
		Role role = require(roleId);
		if (role.isSystemRole()) {
			throw new ApiException(IdentityErrorCode.SYSTEM_ROLE_READ_ONLY, "System roles can't be changed");
		}
		return role;
	}

	private Role require(UUID roleId) {
		return roleRepository.findWithPermissionsById(roleId)
				.orElseThrow(() -> new ApiException(IdentityErrorCode.ROLE_NOT_FOUND, "Role not found"));
	}

	private List<Permission> requirePermissions(Set<String> codes) {
		List<Permission> permissions = permissionRepository.findAllByCodeIn(codes);
		if (permissions.size() != codes.size()) {
			Set<String> unknown = new TreeSet<>(codes);
			permissions.forEach(permission -> unknown.remove(permission.getCode()));
			throw new ApiException(IdentityErrorCode.UNKNOWN_PERMISSION,
					"Unknown permission(s): " + String.join(", ", unknown));
		}
		return permissions;
	}

}
