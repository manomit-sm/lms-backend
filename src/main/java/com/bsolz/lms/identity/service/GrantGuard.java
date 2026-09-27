package com.bsolz.lms.identity.service;

import com.bsolz.lms.identity.entity.Permission;
import com.bsolz.lms.identity.entity.Role;
import com.bsolz.lms.identity.exception.IdentityErrorCode;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.security.CurrentUser;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Nobody can hand out permissions they don't hold themselves - via role assignment or role editing. */
final class GrantGuard {

	private GrantGuard() {
	}

	static void requireCanGrantRoles(Collection<Role> roles) {
		Set<String> granted = new TreeSet<>();
		roles.forEach(role -> granted.addAll(role.getPermissionCodes()));
		requireHeld(granted);
	}

	static void requireCanGrantPermissions(Collection<Permission> permissions) {
		Set<String> granted = new TreeSet<>();
		permissions.forEach(permission -> granted.add(permission.getCode()));
		requireHeld(granted);
	}

	private static void requireHeld(Set<String> granted) {
		Set<String> missing = new TreeSet<>(granted);
		missing.removeAll(CurrentUser.require().permissions());
		if (!missing.isEmpty()) {
			throw new ApiException(IdentityErrorCode.PRIVILEGE_ESCALATION,
					"You can't grant permissions you don't have: " + String.join(", ", missing));
		}
	}

}
