package com.bsolz.lms.reporting.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Approved leave taken per leave type between the dates. */
public record LeaveTypeUsageResponse(LocalDate from, LocalDate to, String scope, List<LeaveType> leaveTypes) {

	/**
	 * @param requests approved requests with days in the range
	 * @param employees distinct employees who took it
	 */
	public record LeaveType(LeaveTypeRef leaveType, int requests, int employees, BigDecimal daysTaken) {
	}

}
