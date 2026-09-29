package com.bsolz.lms.approval.web.dto;

import com.bsolz.lms.shared.validation.HalfDays;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Matches requests of the leave type and/or of at least {@code minDays}; give at least one.
 */
public record WorkflowRuleDto(UUID leaveTypeId, @DecimalMin("0.5") @HalfDays BigDecimal minDays) {
}
