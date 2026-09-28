package com.bsolz.lms.leavepolicy.web.dto;

import com.bsolz.lms.leavepolicy.model.enums.AccrualMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** An empty {@code appliesTo} means the policy applies to everyone. */
public record LeavePolicyResponse(UUID id, LeaveTypeRef leaveType, String name, String description,
		LocalDate effectiveFrom, LocalDate effectiveTo, BigDecimal entitlementDays, AccrualMethod accrualMethod,
		boolean prorateOnJoining, BigDecimal carryForwardMaxDays, Integer carryForwardExpiryMonths,
		BigDecimal negativeBalanceLimit, Integer maxConsecutiveDays, int minNoticeDays, int backdatingAllowedDays,
		BigDecimal attachmentRequiredAfterDays, boolean allowedDuringProbation, boolean sandwichRule, boolean active,
		List<PolicyRule> appliesTo, Instant createdAt, Instant updatedAt) {

	public record LeaveTypeRef(UUID id, String code, String name) {
	}

}
