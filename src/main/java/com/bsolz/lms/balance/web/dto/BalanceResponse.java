package com.bsolz.lms.balance.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** {@code available = allocated + carriedForward + adjusted - expired - used - pending}. */
public record BalanceResponse(UUID id, UUID employeeId, LeaveTypeRef leaveType, LeavePeriodRef leavePeriod,
		BigDecimal allocated, BigDecimal carriedForward, BigDecimal adjusted, BigDecimal expired, BigDecimal used,
		BigDecimal pending, BigDecimal available, Instant updatedAt) {

	public record LeaveTypeRef(UUID id, String code, String name, String color) {
	}

	public record LeavePeriodRef(UUID id, String name, LocalDate startDate, LocalDate endDate) {
	}

}
