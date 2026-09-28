package com.bsolz.lms.leavepolicy.entity;

import com.bsolz.lms.organization.model.enums.EmploymentType;
import com.bsolz.lms.organization.model.enums.Gender;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;

/**
 * One "applies to" rule of a policy: every non-null criterion must match. Stored rules name at least one
 * criterion (Hibernate reads an all-null embeddable as null); a policy without rules applies to everyone,
 * which {@link #EVERYONE} stands for when matching.
 */
@Embeddable
public record ApplicabilityRule(UUID departmentId, UUID designationId, UUID locationId,
		@Enumerated(EnumType.STRING) EmploymentType employmentType, @Enumerated(EnumType.STRING) Gender gender) {

	public static final ApplicabilityRule EVERYONE = new ApplicabilityRule(null, null, null, null, null);

	public boolean isEveryone() {
		return equals(EVERYONE);
	}

}
