package com.bsolz.lms.reporting.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Leave per calendar month.
 *
 * @param scope {@code TENANT} or {@code TEAM} (the caller's reporting line)
 */
public record MonthlyStatisticsResponse(LocalDate from, LocalDate to, String scope, List<Month> months) {

	/**
	 * @param month {@code yyyy-MM}
	 * @param daysTaken approved leave days falling in the month (within the range)
	 * @param submitted requests submitted in the month
	 * @param approved requests approved in the month
	 * @param rejected requests rejected in the month
	 */
	public record Month(String month, BigDecimal daysTaken, List<LeaveTypeDays> byLeaveType, int submitted,
			int approved, int rejected) {
	}

}
