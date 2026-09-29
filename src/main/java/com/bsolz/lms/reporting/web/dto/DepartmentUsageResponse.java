package com.bsolz.lms.reporting.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Approved leave taken per department between the dates. */
public record DepartmentUsageResponse(LocalDate from, LocalDate to, String scope, List<Department> departments) {

	/**
	 * @param headcount current employees (in scope) in the department
	 * @param averageDaysPerEmployee days taken divided by headcount; null without headcount
	 */
	public record Department(UUID departmentId, String departmentName, int headcount, BigDecimal daysTaken,
			BigDecimal averageDaysPerEmployee, List<LeaveTypeDays> byLeaveType) {
	}

}
