package com.bsolz.lms.balance.web.dto;

import java.util.UUID;

/**
 * @param employees current employees considered
 * @param balancesAllocated balances that received their allocation now
 * @param alreadyAllocated balances skipped because they already had one
 */
public record AllocationResult(UUID leavePeriodId, int employees, int balancesAllocated, int alreadyAllocated) {
}
