package com.bsolz.lms.reporting.web.dto;

import com.bsolz.lms.leave.model.enums.LeaveStatus;
import com.bsolz.lms.reporting.model.enums.ExportFormat;
import com.bsolz.lms.reporting.model.enums.ReportType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An export of a report with the same filters as its endpoint; filters a report doesn't use are ignored.
 * Dates default to the current leave period, the balance period to the current one.
 */
public record ExportRequest(@NotNull ReportType report, @NotNull ExportFormat format, LocalDate from, LocalDate to,
		UUID employeeId, UUID departmentId, UUID leaveTypeId, UUID leavePeriodId, LeaveStatus status) {
}
