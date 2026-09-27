package com.bsolz.lms.identity.service;

import com.bsolz.lms.identity.entity.AppUser;
import com.bsolz.lms.identity.entity.Role;
import com.bsolz.lms.identity.exception.IdentityErrorCode;
import com.bsolz.lms.identity.idp.IdentityProviderClient;
import com.bsolz.lms.identity.mapper.IdentityMapper;
import com.bsolz.lms.identity.model.enums.UserStatus;
import com.bsolz.lms.identity.repository.AppUserRepository;
import com.bsolz.lms.identity.repository.RoleRepository;
import com.bsolz.lms.identity.web.dto.InviteUserRequest;
import com.bsolz.lms.identity.web.dto.UserResponse;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.security.CurrentUser;
import com.bsolz.lms.shared.security.SystemRoles;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User administration. Guards: you can only grant permissions you hold, you can't disable yourself,
 * and a tenant always keeps at least one enabled TENANT_ADMIN.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UserAdminService {

	private final AppUserRepository userRepository;

	private final RoleRepository roleRepository;

	private final UserAccountService accountService;

	private final IdentityProviderClient identityProvider;

	private final IdentityMapper mapper;

	private final ApplicationEventPublisher events;

	@Transactional(readOnly = true)
	public Page<UserResponse> list(String search, UserStatus status, Pageable pageable) {
		return userRepository.findAll((root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (search != null && !search.isBlank()) {
				String pattern = "%" + search.trim().toLowerCase(Locale.ROOT)
						.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
				predicates.add(cb.like(cb.lower(root.get("email")), pattern, '\\'));
			}
			if (status != null) {
				predicates.add(cb.equal(root.get("status"), status));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		}, pageable).map(mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public UserResponse get(UUID userId) {
		return mapper.toResponse(require(userId));
	}

	public UserResponse invite(InviteUserRequest request) {
		List<Role> roles = requireRoles(request.roleCodes());
		GrantGuard.requireCanGrantRoles(roles);
		return mapper.toResponse(accountService.invite(request.email(), roles));
	}

	public UserResponse updateRoles(UUID userId, Set<String> roleCodes) {
		AppUser user = require(userId);
		List<Role> roles = requireRoles(roleCodes);
		List<Role> added = roles.stream().filter(role -> !user.hasRole(role.getCode())).toList();
		GrantGuard.requireCanGrantRoles(added);
		user.replaceRoles(roles);
		userRepository.flush();
		requireEnabledTenantAdmin();
		events.publishEvent(UserAccessChanged.inCurrentTenant());
		return mapper.toResponse(user);
	}

	public UserResponse disable(UUID userId) {
		AppUser user = require(userId);
		if (userId.equals(CurrentUser.require().userId())) {
			throw new ApiException(IdentityErrorCode.CANNOT_DISABLE_SELF, "You can't disable your own user");
		}
		if (!user.isDisabled()) {
			user.disable();
			userRepository.flush();
			requireEnabledTenantAdmin();
			identityProvider.disableUser(user.getEmail());
			events.publishEvent(UserAccessChanged.inCurrentTenant());
		}
		return mapper.toResponse(user);
	}

	public UserResponse enable(UUID userId) {
		AppUser user = require(userId);
		if (user.isDisabled()) {
			user.enable();
			identityProvider.enableUser(user.getEmail());
			events.publishEvent(UserAccessChanged.inCurrentTenant());
		}
		return mapper.toResponse(user);
	}

	private void requireEnabledTenantAdmin() {
		if (userRepository.countWithRoleAndStatusNot(SystemRoles.TENANT_ADMIN, UserStatus.DISABLED) == 0) {
			throw new ApiException(IdentityErrorCode.LAST_TENANT_ADMIN,
					"The organisation must keep at least one enabled " + SystemRoles.TENANT_ADMIN);
		}
	}

	private List<Role> requireRoles(Set<String> roleCodes) {
		List<Role> roles = roleRepository.findAllByCodeIn(roleCodes);
		if (roles.size() != roleCodes.size()) {
			Set<String> unknown = new TreeSet<>(roleCodes);
			unknown.removeAll(new HashSet<>(roles.stream().map(Role::getCode).toList()));
			throw new ApiException(IdentityErrorCode.UNKNOWN_ROLE, "Unknown role(s): " + String.join(", ", unknown));
		}
		return roles;
	}

	private AppUser require(UUID userId) {
		return userRepository.findWithRolesById(userId)
				.orElseThrow(() -> new ApiException(IdentityErrorCode.USER_NOT_FOUND, "User not found"));
	}

}
