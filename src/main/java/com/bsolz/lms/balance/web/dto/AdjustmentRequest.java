package com.bsolz.lms.balance.web.dto;

import com.bsolz.lms.shared.validation.HalfDays;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param leavePeriodId defaults to the current leave period
 * @param amount days to add (positive) or remove (negative)
 * @param reason recorded in the ledger
 */
public record AdjustmentRequest(@NotNull UUID employeeId, @NotNull UUID leaveTypeId, UUID leavePeriodId,
		@NotNull @HalfDays @DecimalMin("-365") @DecimalMax("365") BigDecimal amount,
		@NotBlank @Size(max = 500) String reason) {
}
