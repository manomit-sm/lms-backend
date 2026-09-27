package com.bsolz.lms.identity.service;

import com.bsolz.lms.identity.entity.AppUser;
import com.bsolz.lms.identity.exception.IdentityErrorCode;
import com.bsolz.lms.identity.repository.AppUserRepository;
import com.bsolz.lms.identity.web.dto.MeResponse;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.security.CurrentUser;
import com.bsolz.lms.shared.security.LmsPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MeService {

	private final AppUserRepository userRepository;

	private final OrganizationApi organizationApi;

	public MeResponse me() {
		LmsPrincipal principal = CurrentUser.require();
		AppUser user = userRepository.findWithRolesById(principal.userId())
				.orElseThrow(() -> new ApiException(IdentityErrorCode.USER_NOT_FOUND, "User not found"));
		return new MeResponse(user.getId(), user.getEmail(), user.getStatus(),
				new MeResponse.Tenant(principal.tenant().id(), principal.tenant().key()), user.getRoleCodes(),
				user.getPermissionCodes(),
				user.getEmployeeId() == null ? null : organizationApi.findEmployee(user.getEmployeeId()).orElse(null));
	}

}
