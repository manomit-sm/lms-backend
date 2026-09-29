package com.bsolz.lms.leave.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * What a request would draw and whether it can be submitted.
 *
 * @param leavePeriodId null when no leave period covers the start date
 * @param available the balance now; null for leave types without a balance
 * @param availableAfter the balance if this request is approved; null for leave types without a balance
 * @param valid true when there are no violations
 */
public record PreviewResponse(UUID leaveTypeId, UUID leavePeriodId, BigDecimal totalDays, BigDecimal available,
		BigDecimal availableAfter, boolean valid, List<ViolationDto> violations, List<LeaveDayDto> days) {
}
