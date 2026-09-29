package com.bsolz.lms.reporting.web.dto;

import com.bsolz.lms.leave.model.enums.LeaveStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveHistoryRow(UUID requestId, UUID employeeId, String employeeCode, String employeeName,
		String departmentName, LeaveTypeRef leaveType, LocalDate startDate, LocalDate endDate, BigDecimal totalDays,
		LeaveStatus status, Instant submittedAt, Instant decidedAt) {
}
