package com.bsolz.lms.approval.entity;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.UUID;

/** When a workflow applies: every criterion given must match (at least one is always given). */
@Embeddable
public record WorkflowRule(UUID leaveTypeId, BigDecimal minDays) {

	public boolean matches(UUID requestLeaveTypeId, BigDecimal days) {
		return (leaveTypeId == null || leaveTypeId.equals(requestLeaveTypeId))
				&& (minDays == null || days.compareTo(minDays) >= 0);
	}

}
