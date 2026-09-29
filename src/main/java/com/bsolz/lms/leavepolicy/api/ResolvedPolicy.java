package com.bsolz.lms.leavepolicy.api;

import com.bsolz.lms.leavepolicy.model.enums.AccrualMethod;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The policy that governs one employee's use of one leave type on a given date. Null limits mean
 * "no limit" / "never required".
 *
 * @param entitlementDays per leave period
 * @param negativeBalanceLimit how far below zero the balance may go
 * @param backdatingAllowedDays how many days in the past a request may start; 0 means not at all
 * @param sandwichRule whether weekends and holidays inside a request count as leave days
 */
public record ResolvedPolicy(UUID policyId, String policyName, UUID leaveTypeId, BigDecimal entitlementDays,
		AccrualMethod accrualMethod, boolean prorateOnJoining, BigDecimal carryForwardMaxDays,
		Integer carryForwardExpiryMonths, BigDecimal negativeBalanceLimit, Integer maxConsecutiveDays,
		int minNoticeDays, int backdatingAllowedDays, BigDecimal attachmentRequiredAfterDays,
		boolean allowedDuringProbation, boolean sandwichRule) {
}
