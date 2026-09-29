package com.bsolz.lms.leave.web.dto;

import com.bsolz.lms.approval.api.ApprovalView;
import com.bsolz.lms.leave.model.enums.DaySession;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A leave request with its days, attachments, approvals (the request's, then any cancellation's) and
 * status history.
 */
public record LeaveRequestDetail(UUID id, EmployeeRef employee, LeaveTypeRef leaveType, UUID leavePeriodId,
		LocalDate startDate, LocalDate endDate, DaySession startSession, DaySession endSession, BigDecimal totalDays,
		String reason, LeaveStatus status, String cancellationReason, Instant createdAt, Instant decidedAt,
		List<LeaveDayDto> days, List<AttachmentResponse> attachments, List<ApprovalView> approvals,
		List<HistoryEntry> history) {
}
