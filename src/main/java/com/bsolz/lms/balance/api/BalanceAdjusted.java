package com.bsolz.lms.balance.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published when HR adjusts a balance by hand.
 *
 * @param transactionId the ledger entry recording the adjustment
 * @param actorUserId who made it
 */
public record BalanceAdjusted(UUID tenantId, UUID transactionId, UUID employeeId, UUID leaveTypeId,
		UUID leavePeriodId, BigDecimal amount, String reason, UUID actorUserId) {
}
