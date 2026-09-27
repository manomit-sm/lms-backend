package com.bsolz.lms.organization.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Employee lookups and the reporting hierarchy, for other modules. All calls need a tenant bound. */
public interface OrganizationApi {

	Optional<EmployeeSummary> findEmployee(UUID employeeId);

	List<EmployeeSummary> findEmployees(Collection<UUID> employeeIds);

	/** Everyone reporting to the manager, directly or indirectly (the manager excluded). */
	Set<UUID> findReportingLine(UUID managerId);

	/** The employee's managers from the direct manager upwards. */
	List<UUID> findManagerChain(UUID employeeId);

	/** Whether the employee is in the manager's reporting line (direct or indirect). */
	boolean isInReportingLine(UUID managerId, UUID employeeId);

}
