package com.bsolz.lms.reporting.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What happened to the requests submitted between the dates. */
public record DecisionsResponse(LocalDate from, LocalDate to, String scope, Stats total, List<Stats> byLeaveType) {

	/**
	 * Requests are counted as approved or rejected by their decision, even if cancelled later.
	 *
	 * @param leaveType null for the total
	 * @param approvalRate approved / (approved + rejected); null before any decision
	 * @param averageHoursToDecision from submission to approval or rejection; null before any decision
	 */
	public record Stats(LeaveTypeRef leaveType, int submitted, int approved, int rejected, int withdrawn,
			int cancelled, int pending, BigDecimal approvalRate, BigDecimal averageHoursToDecision) {
	}

}
