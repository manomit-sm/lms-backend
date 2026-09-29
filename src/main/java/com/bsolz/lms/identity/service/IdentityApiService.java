package com.bsolz.lms.identity.service;

import com.bsolz.lms.identity.api.IdentityApi;
import com.bsolz.lms.identity.api.UserSummary;
import com.bsolz.lms.identity.model.enums.UserStatus;
import com.bsolz.lms.identity.repository.AppUserRepository;
import com.bsolz.lms.identity.repository.RoleRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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

}
