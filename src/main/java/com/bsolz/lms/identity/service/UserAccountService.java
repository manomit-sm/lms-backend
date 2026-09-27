package com.bsolz.lms.identity.service;

import com.bsolz.lms.identity.entity.AppUser;
import com.bsolz.lms.identity.entity.Role;
import com.bsolz.lms.identity.exception.IdentityErrorCode;
import com.bsolz.lms.identity.idp.IdentityProviderClient;
import com.bsolz.lms.identity.repository.AppUserRepository;
import com.bsolz.lms.identity.repository.RoleRepository;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.security.SystemRoles;
import com.bsolz.lms.shared.tenancy.TenantContext;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates users and keeps them in step with the identity provider. Every operation is idempotent
 * (safe to retry from a resubmitted event): the user row is saved first, then the provider is asked
 * to invite; if the provider fails the transaction rolls back and the retry finds the provider user
 * by email.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserAccountService {

	private final AppUserRepository userRepository;

	private final RoleRepository roleRepository;

	private final IdentityProviderClient identityProvider;

	private final ApplicationEventPublisher events;

	/** A new employee becomes a user with the EMPLOYEE role (reusing an existing user with that email). */
	public AppUser provisionForEmployee(UUID employeeId, String email) {
		AppUser user = userRepository.findByEmployeeId(employeeId)
				.or(() -> userRepository.findByEmailIgnoreCase(email))
				.orElseGet(() -> AppUser.invite(email));
		if (user.getEmployeeId() == null) {
			user.setEmployeeId(employeeId);
		}
		else if (!user.getEmployeeId().equals(employeeId)) {
			throw new ApiException(IdentityErrorCode.EMAIL_LINKED_TO_OTHER_EMPLOYEE,
					"User " + email + " is already linked to another employee");
		}
		user.addRole(requireRole(SystemRoles.EMPLOYEE));
		return inviteAndSave(user);
	}

	public void disableForExitedEmployee(UUID employeeId) {
		userRepository.findByEmployeeId(employeeId).filter(user -> !user.isDisabled()).ifPresent(user -> {
			user.disable();
			identityProvider.disableUser(user.getEmail());
			events.publishEvent(UserAccessChanged.inCurrentTenant());
			log.info("Disabled user {} of exited employee {}", user.getEmail(), employeeId);
		});
	}

	public AppUser ensureTenantAdmin(String email) {
		AppUser user = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> AppUser.invite(email));
		user.addRole(requireRole(SystemRoles.TENANT_ADMIN));
		return inviteAndSave(user);
	}

	/** Invites a new user with the given roles; the caller has already checked it may grant them. */
	public AppUser invite(String email, Collection<Role> roles) {
		if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
			throw new ApiException(IdentityErrorCode.USER_ALREADY_EXISTS, "A user with email " + email + " exists");
		}
		AppUser user = AppUser.invite(email);
		user.replaceRoles(roles);
		return inviteAndSave(user);
	}

	private AppUser inviteAndSave(AppUser user) {
		AppUser saved = userRepository.save(user);
		if (saved.getIdpSubject() == null) {
			saved.setIdpSubject(identityProvider.inviteUser(saved.getEmail(), TenantContext.require().id()));
		}
		events.publishEvent(UserAccessChanged.inCurrentTenant());
		return saved;
	}

	private Role requireRole(String code) {
		return roleRepository.findByCode(code)
				.orElseThrow(() -> new IllegalStateException("System role " + code + " is missing"));
	}

}
