package com.bsolz.lms.balance.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published when a leave period's year-end processing is done and the period is closed.
 *
 * @param carriedForwardDays days moved into the next period, over all balances
 * @param lapsedDays unused days that lapsed, over all balances
 */
public record LeavePeriodRolledOver(UUID tenantId, UUID leavePeriodId, String leavePeriodName, UUID nextLeavePeriodId,
		int balances, BigDecimal carriedForwardDays, BigDecimal lapsedDays) {
}
