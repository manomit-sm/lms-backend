package com.bsolz.lms.leavepolicy.entity;

import com.bsolz.lms.leavepolicy.model.enums.AccrualMethod;
import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** The rules for one leave type, for the employees its applicability rules match, between its effective dates. */
@Getter
@Setter
@Entity
@Table(name = "leave_policy")
public class LeavePolicy extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private LeaveType leaveType;

	private String name;

	private String description;

	/** Null: since always. */
	private LocalDate effectiveFrom;

	/** Inclusive; null: open-ended. */
	private LocalDate effectiveTo;

	private BigDecimal entitlementDays;

	@Enumerated(EnumType.STRING)
	private AccrualMethod accrualMethod;

	private boolean prorateOnJoining;

	private BigDecimal carryForwardMaxDays;

	private Integer carryForwardExpiryMonths;

	private BigDecimal negativeBalanceLimit;

	private Integer maxConsecutiveDays;

	private int minNoticeDays;

	private int backdatingAllowedDays;

	private BigDecimal attachmentRequiredAfterDays;

	private boolean allowedDuringProbation;

	private boolean sandwichRule;

	private boolean active = true;

	/**
	 * Empty: applies to everyone. A list (bag) so Hibernate rewrites the rows as a whole on change: rules
	 * have nullable columns and no identity.
	 */
	@ElementCollection
	@CollectionTable(name = "leave_policy_applicability", joinColumns = @JoinColumn(name = "leave_policy_id"))
	private List<ApplicabilityRule> appliesTo = new ArrayList<>();

	/** The rules to match against: {@link ApplicabilityRule#EVERYONE} when the policy has none. */
	public List<ApplicabilityRule> getEffectiveRules() {
		return appliesTo.isEmpty() ? List.of(ApplicabilityRule.EVERYONE) : appliesTo;
	}

	public boolean isInEffectOn(LocalDate date) {
		return (effectiveFrom == null || !date.isBefore(effectiveFrom))
				&& (effectiveTo == null || !date.isAfter(effectiveTo));
	}

	/** Whether the two policies' effective date ranges share at least one day. */
	public boolean overlaps(LocalDate from, LocalDate to) {
		return (effectiveFrom == null || to == null || !effectiveFrom.isAfter(to))
				&& (effectiveTo == null || from == null || !effectiveTo.isBefore(from));
	}

}
