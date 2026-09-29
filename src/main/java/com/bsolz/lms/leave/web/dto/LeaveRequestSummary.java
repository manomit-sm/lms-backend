package com.bsolz.lms.leave.web.dto;

import com.bsolz.lms.leave.model.enums.DaySession;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestSummary(UUID id, EmployeeRef employee, LeaveTypeRef leaveType, LocalDate startDate,
		LocalDate endDate, DaySession startSession, DaySession endSession, BigDecimal totalDays, LeaveStatus status,
		Instant createdAt, Instant decidedAt) {
}
