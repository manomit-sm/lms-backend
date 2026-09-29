package com.bsolz.lms.reporting.domain;

import java.util.UUID;

/**
 * Whose data a report covers: the whole tenant, or a manager and everyone in their reporting line.
 *
 * @param managerEmployeeId the head of the reporting line; null when tenant-wide
 */
public record ReportScope(boolean tenantWide, UUID managerEmployeeId) {

	/**
	 * A recursive CTE named {@code scope_employee} listing the ids in scope. Binds {@code :tenantWide} and
	 * {@code :scopeManager}.
	 */
	public static final String CTE = """
			WITH RECURSIVE scope_employee AS (
			    SELECT id FROM employee WHERE :tenantWide OR id = CAST(:scopeManager AS uuid)
			    UNION
			    SELECT e.id FROM employee e JOIN scope_employee s ON e.reporting_manager_id = s.id
			    WHERE NOT :tenantWide
			)
			""";

	public static ReportScope tenant() {
		return new ReportScope(true, null);
	}

	public static ReportScope reportingLine(UUID managerEmployeeId) {
		return new ReportScope(false, managerEmployeeId);
	}

	public String name() {
		return tenantWide ? "TENANT" : "TEAM";
	}

}
