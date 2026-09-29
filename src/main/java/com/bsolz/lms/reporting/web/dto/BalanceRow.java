package com.bsolz.lms.reporting.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One employee's balance of one leave type in the period. */
public record BalanceRow(UUID employeeId, String employeeCode, String employeeName, String departmentName,
		LeaveTypeRef leaveType, BigDecimal allocated, BigDecimal carriedForward, BigDecimal adjusted,
		BigDecimal expired, BigDecimal carriedOut, BigDecimal used, BigDecimal pending, BigDecimal available) {
}
