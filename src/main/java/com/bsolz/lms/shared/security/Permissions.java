package com.bsolz.lms.shared.security;

import java.util.List;

/**
 * Permission codes, as seeded in the tenant changelog ({@code 004-seed-roles-permissions.sql}) and
 * granted as authorities by {@link LmsPrincipal}. Use them in {@code @PreAuthorize} as literals,
 * e.g. {@code hasAuthority('EMPLOYEE_MANAGE')}. Adding a code here requires a changeset inserting it.
 */
public final class Permissions {

	public static final String ORGANIZATION_MANAGE = "ORGANIZATION_MANAGE";

	public static final String EMPLOYEE_VIEW_ALL = "EMPLOYEE_VIEW_ALL";

	public static final String EMPLOYEE_VIEW_TEAM = "EMPLOYEE_VIEW_TEAM";

	public static final String EMPLOYEE_MANAGE = "EMPLOYEE_MANAGE";

	public static final String USER_MANAGE = "USER_MANAGE";

	public static final String ROLE_MANAGE = "ROLE_MANAGE";

	public static final String LEAVE_APPLY = "LEAVE_APPLY";

	public static final String LEAVE_APPROVE = "LEAVE_APPROVE";

	public static final String LEAVE_MANAGE = "LEAVE_MANAGE";

	public static final String LEAVE_POLICY_MANAGE = "LEAVE_POLICY_MANAGE";

	public static final String BALANCE_ADJUST = "BALANCE_ADJUST";

	public static final String HOLIDAY_MANAGE = "HOLIDAY_MANAGE";

	public static final String WORKFLOW_MANAGE = "WORKFLOW_MANAGE";

	public static final String REPORT_VIEW_TEAM = "REPORT_VIEW_TEAM";

	public static final String REPORT_VIEW_ALL = "REPORT_VIEW_ALL";

	public static final String REPORT_EXPORT = "REPORT_EXPORT";

	public static final String SETTINGS_MANAGE = "SETTINGS_MANAGE";

	public static final String AUDIT_VIEW = "AUDIT_VIEW";

	public static final List<String> ALL = List.of(ORGANIZATION_MANAGE, EMPLOYEE_VIEW_ALL, EMPLOYEE_VIEW_TEAM,
			EMPLOYEE_MANAGE, USER_MANAGE, ROLE_MANAGE, LEAVE_APPLY, LEAVE_APPROVE, LEAVE_MANAGE, LEAVE_POLICY_MANAGE,
			BALANCE_ADJUST, HOLIDAY_MANAGE, WORKFLOW_MANAGE, REPORT_VIEW_TEAM, REPORT_VIEW_ALL, REPORT_EXPORT,
			SETTINGS_MANAGE, AUDIT_VIEW);

	private Permissions() {
	}

}
