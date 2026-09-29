package com.bsolz.lms.leavepolicy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.bsolz.lms.leavepolicy.entity.ApplicabilityRule;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.model.enums.EmploymentStatus;
import com.bsolz.lms.organization.model.enums.EmploymentType;
import com.bsolz.lms.organization.model.enums.Gender;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PolicyMatcherTest {

	private final UUID engineering = UUID.randomUUID();

	private final UUID senior = UUID.randomUUID();

	private final UUID bengaluru = UUID.randomUUID();

	private final EmployeeSummary employee = new EmployeeSummary(UUID.randomUUID(), "E1", "Ada", "L", "ada@x.test",
			Gender.FEMALE, engineering, senior, bengaluru, null, null, EmploymentType.FULL_TIME,
			EmploymentStatus.ACTIVE, LocalDate.of(2024, 1, 1), null, null);

	@Test
	void everyGivenCriterionMustMatch() {
		assertThat(PolicyMatcher.matches(ApplicabilityRule.EVERYONE, employee)).isTrue();
		assertThat(PolicyMatcher.matches(rule(engineering, null, bengaluru, null, Gender.FEMALE), employee)).isTrue();
		assertThat(PolicyMatcher.matches(rule(engineering, null, bengaluru, null, Gender.MALE), employee)).isFalse();
		assertThat(PolicyMatcher.matches(rule(UUID.randomUUID(), null, null, null, null), employee)).isFalse();
		assertThat(PolicyMatcher.matches(rule(null, null, null, EmploymentType.INTERN, null), employee)).isFalse();
	}

	@Test
	void aHigherRankedCriterionBeatsAnyCombinationOfLowerOnes() {
		int department = PolicyMatcher.specificity(rule(engineering, null, null, null, null));
		int everythingElse = PolicyMatcher.specificity(rule(null, senior, bengaluru, EmploymentType.FULL_TIME, Gender.FEMALE));
		int designation = PolicyMatcher.specificity(rule(null, senior, null, null, null));
		int locationTypeGender = PolicyMatcher.specificity(rule(null, null, bengaluru, EmploymentType.FULL_TIME, Gender.FEMALE));

		assertThat(department).isGreaterThan(everythingElse);
		assertThat(designation).isGreaterThan(locationTypeGender);
		assertThat(PolicyMatcher.specificity(ApplicabilityRule.EVERYONE)).isZero();
	}

	@Test
	void bestMatchIsTheMostSpecificMatchingRule() {
		List<ApplicabilityRule> rules = List.of(ApplicabilityRule.EVERYONE, rule(null, null, bengaluru, null, null),
				rule(UUID.randomUUID(), senior, null, null, null));

		assertThat(PolicyMatcher.bestMatch(rules, employee)).hasValue(4);
		assertThat(PolicyMatcher.bestMatch(List.of(rule(null, null, null, null, Gender.MALE)), employee)).isEmpty();
	}

	private static ApplicabilityRule rule(UUID department, UUID designation, UUID location, EmploymentType type,
			Gender gender) {
		return new ApplicabilityRule(department, designation, location, type, gender);
	}

}
