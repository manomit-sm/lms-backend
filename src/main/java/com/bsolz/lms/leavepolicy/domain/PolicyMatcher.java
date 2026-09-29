package com.bsolz.lms.leavepolicy.domain;

import com.bsolz.lms.leavepolicy.entity.ApplicabilityRule;
import com.bsolz.lms.organization.api.EmployeeSummary;
import java.util.Collection;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Matches applicability rules against an employee and ranks how specific a match is. Each criterion has
 * a weight greater than all lower-ranked criteria combined (department 16 &gt; designation 8 &gt;
 * location 4 &gt; employment type 2 &gt; gender 1), so two different sets of criteria never tie.
 */
public final class PolicyMatcher {

	private PolicyMatcher() {
	}

	public static boolean matches(ApplicabilityRule rule, EmployeeSummary employee) {
		return criterionMatches(rule.departmentId(), employee.departmentId())
				&& criterionMatches(rule.designationId(), employee.designationId())
				&& criterionMatches(rule.locationId(), employee.locationId())
				&& criterionMatches(rule.employmentType(), employee.employmentType())
				&& criterionMatches(rule.gender(), employee.gender());
	}

	public static int specificity(ApplicabilityRule rule) {
		return (rule.departmentId() != null ? 16 : 0) + (rule.designationId() != null ? 8 : 0)
				+ (rule.locationId() != null ? 4 : 0) + (rule.employmentType() != null ? 2 : 0)
				+ (rule.gender() != null ? 1 : 0);
	}

	/** The specificity of the most specific rule matching the employee; empty if none matches. */
	public static OptionalInt bestMatch(Collection<ApplicabilityRule> rules, EmployeeSummary employee) {
		return rules.stream().filter(rule -> matches(rule, employee)).mapToInt(PolicyMatcher::specificity).max();
	}

	private static boolean criterionMatches(Object required, Object actual) {
		return required == null || Objects.equals(required, actual);
	}

}
