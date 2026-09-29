package com.bsolz.lms.reporting.service;

import com.bsolz.lms.leave.model.enums.LeaveStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Report filters, resolved: {@code from}/{@code to} are always set (inclusive); the others are optional
 * and each report uses those that make sense for it.
 */
public record ReportCriteria(LocalDate from, LocalDate to, UUID employeeId, UUID departmentId, UUID leaveTypeId,
		UUID leavePeriodId, LeaveStatus status) {
}
