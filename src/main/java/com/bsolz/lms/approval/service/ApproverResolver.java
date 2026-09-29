package com.bsolz.lms.approval.service;

import com.bsolz.lms.approval.entity.WorkflowStep;
import com.bsolz.lms.identity.api.IdentityApi;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.OrganizationApi;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns a workflow step into the users who may decide it, for one requester. The requester is never
 * their own approver: a step left with nobody else is skipped, with the reason recorded.
 */
@Component
@RequiredArgsConstructor
class ApproverResolver {

	private final OrganizationApi organizationApi;

	private final IdentityApi identityApi;

	Resolution resolve(WorkflowStep step, EmployeeSummary requester, UUID requesterUserId) {
		Resolution resolution = switch (step.approverType()) {
			case REPORTING_MANAGER -> requester.reportingManagerId() == null
					? Resolution.skipped("Reporting manager", "The requester has no reporting manager")
					: employee("Reporting manager", requester.reportingManagerId());
			case SKIP_LEVEL_MANAGER -> {
				List<UUID> chain = organizationApi.findManagerChain(requester.id());
				yield chain.size() < 2 ? Resolution.skipped("Skip-level manager", "The requester has no skip-level manager")
						: employee("Skip-level manager", chain.get(1));
			}
			case ROLE -> {
				Set<UUID> users = identityApi.findEnabledUserIdsWithRole(step.roleCode());
				yield users.isEmpty()
						? Resolution.skipped("Role " + step.roleCode(), "No enabled user has the role " + step.roleCode())
						: new Resolution("Role " + step.roleCode(), users, null);
			}
			case EMPLOYEE -> employee(organizationApi.findEmployee(step.employeeId())
					.map(EmployeeSummary::fullName).orElse("Employee"), step.employeeId());
		};
		if (resolution.userIds().contains(requesterUserId)) {
			Set<UUID> others = new HashSet<>(resolution.userIds());
			others.remove(requesterUserId);
			return others.isEmpty()
					? Resolution.skipped(resolution.description(), "Only the requester could approve this step")
					: new Resolution(resolution.description(), others, null);
		}
		return resolution;
	}

	private Resolution employee(String description, UUID employeeId) {
		return identityApi.findEnabledUserIdByEmployeeId(employeeId)
				.map(userId -> new Resolution(description, Set.of(userId), null))
				.orElseGet(() -> Resolution.skipped(description, description + " has no enabled user account"));
	}

	/** @param skipReason why nobody can approve; null when {@code userIds} is not empty */
	record Resolution(String description, Set<UUID> userIds, String skipReason) {

		static Resolution skipped(String description, String reason) {
			return new Resolution(description, Set.of(), reason);
		}

	}

}
