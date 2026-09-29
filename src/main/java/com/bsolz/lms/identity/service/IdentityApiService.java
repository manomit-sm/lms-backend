package com.bsolz.lms.identity.service;

import com.bsolz.lms.identity.api.IdentityApi;
import com.bsolz.lms.identity.api.UserSummary;
import com.bsolz.lms.identity.entity.AppUser;
import com.bsolz.lms.identity.model.enums.UserStatus;
import com.bsolz.lms.identity.repository.AppUserRepository;
import com.bsolz.lms.identity.repository.RoleRepository;
import com.bsolz.lms.organization.api.OrganizationApi;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class IdentityApiService implements IdentityApi {

	private final UserAccountService accountService;

	private final AppUserRepository userRepository;

	private final RoleRepository roleRepository;

	private final OrganizationApi organizationApi;

	@Override
	public UUID ensureTenantAdmin(String email) {
		return accountService.ensureTenantAdmin(email).getId();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<UUID> findEnabledUserIdByEmployeeId(UUID employeeId) {
		return userRepository.findIdByEmployeeIdAndStatusNot(employeeId, UserStatus.DISABLED);
	}

	@Override
	@Transactional(readOnly = true)
	public Set<UUID> findEnabledUserIdsWithRole(String roleCode) {
		return new HashSet<>(userRepository.findIdsWithRoleAndStatusNot(roleCode, UserStatus.DISABLED));
	}

	@Override
	@Transactional(readOnly = true)
	public boolean roleExists(String roleCode) {
		return roleRepository.existsByCode(roleCode);
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserSummary> findUsers(Collection<UUID> userIds) {
		return userRepository.findAllById(userIds).stream()
				.map(user -> new UserSummary(user.getId(), user.getEmail(), user.getEmployeeId(), !user.isDisabled()))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Map<UUID, String> findDisplayNames(Collection<UUID> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		List<AppUser> users = userRepository.findAllById(userIds);
		Map<UUID, String> employeeNames = new HashMap<>();
		organizationApi.findEmployees(users.stream().map(AppUser::getEmployeeId).filter(Objects::nonNull).toList())
				.forEach(employee -> employeeNames.put(employee.id(), employee.fullName()));
		Map<UUID, String> names = new HashMap<>();
		users.forEach(user -> names.put(user.getId(),
				user.getEmployeeId() != null && employeeNames.containsKey(user.getEmployeeId())
						? employeeNames.get(user.getEmployeeId()) : user.getEmail()));
		return names;
	}

}
