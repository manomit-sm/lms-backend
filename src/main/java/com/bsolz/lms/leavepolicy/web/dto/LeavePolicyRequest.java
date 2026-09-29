package com.bsolz.lms.leavepolicy.web.dto;

import com.bsolz.lms.leavepolicy.model.enums.AccrualMethod;
import com.bsolz.lms.shared.validation.HalfDays;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A leave policy. Optional numbers left out mean "no limit"/"never", except {@code carryForwardMaxDays},
 * {@code negativeBalanceLimit}, {@code minNoticeDays} and {@code backdatingAllowedDays}, which default to 0.
 *
 * @param leaveTypeId fixed once the policy exists
 * @param effectiveFrom null: since always
 * @param effectiveTo inclusive; null: open-ended
 * @param entitlementDays per leave period
 * @param allowedDuringProbation defaults to true
 * @param sandwichRule count weekends and holidays inside a request; defaults to false
 * @param active defaults to true
 * @param appliesTo rules, any of which must match; empty, absent or containing a rule without criteria:
 * everyone
 */
public record LeavePolicyRequest(@NotNull UUID leaveTypeId, @NotBlank @Size(max = 150) String name,
		@Size(max = 500) String description, LocalDate effectiveFrom, LocalDate effectiveTo,
		@NotNull @DecimalMin("0") @HalfDays BigDecimal entitlementDays, @NotNull AccrualMethod accrualMethod,
		@NotNull Boolean prorateOnJoining, @DecimalMin("0") @HalfDays BigDecimal carryForwardMaxDays,
		@Positive Integer carryForwardExpiryMonths, @DecimalMin("0") @HalfDays BigDecimal negativeBalanceLimit,
		@Positive Integer maxConsecutiveDays, @PositiveOrZero Integer minNoticeDays,
		@PositiveOrZero Integer backdatingAllowedDays, @DecimalMin("0") @HalfDays BigDecimal attachmentRequiredAfterDays,
		Boolean allowedDuringProbation, Boolean sandwichRule, Boolean active,
		@Size(max = 50) List<@Valid PolicyRule> appliesTo) {
}
