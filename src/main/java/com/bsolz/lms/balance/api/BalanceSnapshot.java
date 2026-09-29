package com.bsolz.lms.balance.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An employee's balance of one leave type for one leave period.
 * {@code available = allocated + carriedForward + adjusted - expired - carriedOut - used - pending}.
 *
 * @param adjusted net manual adjustments; may be negative
 * @param carriedOut unused days moved to the next period at year end
 * @param used approved leave
 * @param pending held by requests awaiting approval
 */
public record BalanceSnapshot(UUID balanceId, UUID employeeId, UUID leaveTypeId, UUID leavePeriodId,
		BigDecimal allocated, BigDecimal carriedForward, BigDecimal adjusted, BigDecimal expired, BigDecimal carriedOut,
		BigDecimal used,
		BigDecimal pending, BigDecimal available) {
}
