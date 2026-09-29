package com.bsolz.lms.balance.web.dto;

import java.util.Set;
import java.util.UUID;

/**
 * @param leavePeriodId defaults to the current leave period
 * @param leaveTypeIds defaults to every active, balance-tracked leave type
 */
public record AllocationRequest(UUID leavePeriodId, Set<UUID> leaveTypeIds) {
}
