package com.bsolz.lms.identity.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Identity operations for other modules. Calls need the target tenant bound. */
public interface IdentityApi {

	/**
	 * Makes {@code email} a tenant admin: creates the user if needed, grants TENANT_ADMIN and sends
	 * the identity-provider invitation. Idempotent.
	 *
	 * @return the user id
	 */
	UUID ensureTenantAdmin(String email);

	/** The enabled user linked to the employee, if any. */
	Optional<UUID> findEnabledUserIdByEmployeeId(UUID employeeId);

	/** Enabled users holding the role. */
	Set<UUID> findEnabledUserIdsWithRole(String roleCode);

	boolean roleExists(String roleCode);

	List<UserSummary> findUsers(Collection<UUID> userIds);

	/**
	 * How to show each user to other people: their employee's full name, else their email. Unknown
	 * ids are absent from the result.
	 */
	Map<UUID, String> findDisplayNames(Collection<UUID> userIds);

}
