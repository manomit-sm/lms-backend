package com.bsolz.lms.leave.model.enums;

import java.math.BigDecimal;

/** How much of a day a request covers. */
public enum DaySession {

	FULL_DAY(BigDecimal.ONE),
	FIRST_HALF(new BigDecimal("0.5")),
	SECOND_HALF(new BigDecimal("0.5"));

	private final BigDecimal days;

	DaySession(BigDecimal days) {
		this.days = days;
	}

	public BigDecimal days() {
		return days;
	}

	public boolean isHalf() {
		return this != FULL_DAY;
	}

}
