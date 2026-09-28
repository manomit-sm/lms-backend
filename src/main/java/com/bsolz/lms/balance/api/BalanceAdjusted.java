package com.bsolz.lms.balance.api;

import java.math.BigDecimal;
import java.util.UUID;

/** Published when HR adjusts a balance by hand. */
public record BalanceAdjusted(UUID tenantId, UUID employeeId, UUID leaveTypeId, UUID leavePeriodId, BigDecimal amount,
		String reason) {
}
