package com.bsolz.lms.balance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param leaveDate the leave's first day: picks the leave period and the policy in effect
 * @param days whole or half days, positive
 * @param referenceId the leave request; later release/consume/reverse calls name it
 */
public record HoldRequest(UUID employeeId, UUID leaveTypeId, LocalDate leaveDate, BigDecimal days, UUID referenceId) {
}
