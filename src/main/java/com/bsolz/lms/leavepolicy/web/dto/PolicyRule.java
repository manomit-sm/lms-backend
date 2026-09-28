package com.bsolz.lms.leavepolicy.web.dto;

import com.bsolz.lms.organization.model.enums.EmploymentType;
import com.bsolz.lms.organization.model.enums.Gender;
import java.util.UUID;

/** Who a policy applies to. Every criterion given must match; a rule without criteria matches everyone. */
public record PolicyRule(UUID departmentId, UUID designationId, UUID locationId, EmploymentType employmentType,
		Gender gender) {
}
