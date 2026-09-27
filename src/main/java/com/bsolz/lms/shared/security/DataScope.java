package com.bsolz.lms.shared.security;

/**
 * Whose data a user may see for a given kind of record: only their own, their reporting line
 * (direct and indirect reports, plus themselves), or the whole tenant. Each module derives the
 * scope from the relevant {@code *_VIEW_ALL} / {@code *_VIEW_TEAM} permissions.
 */
public enum DataScope {

	SELF,
	TEAM,
	TENANT;

	public static DataScope from(LmsPrincipal principal, String viewAllPermission, String viewTeamPermission) {
		if (principal.permissions().contains(viewAllPermission)) {
			return TENANT;
		}
		if (principal.permissions().contains(viewTeamPermission) && principal.employeeId() != null) {
			return TEAM;
		}
		return SELF;
	}

}
