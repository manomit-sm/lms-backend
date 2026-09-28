package com.bsolz.lms.organization.api;

import com.bsolz.lms.organization.model.enums.OrgUnitType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Employee lookups, the reporting hierarchy and organisation units, for other modules. All calls need a tenant bound. */
public interface OrganizationApi {

	Optional<EmployeeSummary> findEmployee(UUID employeeId);

	List<EmployeeSummary> findEmployees(Collection<UUID> employeeIds);

	/** Every employee who has not exited. */
	List<EmployeeSummary> findCurrentEmployees();

	/** Everyone reporting to the manager, directly or indirectly (the manager excluded). */
	Set<UUID> findReportingLine(UUID managerId);

	/** The employee's managers from the direct manager upwards. */
	List<UUID> findManagerChain(UUID employeeId);

	/** Whether the employee is in the manager's reporting line (direct or indirect). */
	boolean isInReportingLine(UUID managerId, UUID employeeId);

	/** Which of the given ids exist as units of that type. */
	Set<UUID> findExistingUnitIds(OrgUnitType type, Collection<UUID> ids);

	/**
	 * Ids of departments or locations by code, matched case-insensitively and keyed by the upper-cased
	 * code. Codes that don't exist are absent from the result. Designations have no code.
	 */
	Map<String, UUID> findUnitIdsByCode(OrgUnitType type, Collection<String> codes);

}
