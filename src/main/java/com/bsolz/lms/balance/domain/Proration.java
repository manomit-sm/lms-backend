package com.bsolz.lms.balance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Entitlement for someone joining part-way through a leave period: the entitlement times the share of
 * the period's months they are employed for, rounded to the nearest half day. The joining month counts
 * if they join on or before the 15th.
 * <p>
 * Example: 18 days in a January-December period, joining 10 July: 6 of 12 months, 9 days; joining
 * 20 July: 5 of 12 months, 7.5 days.
 */
public final class Proration {

	/** Joining on or before this day of the month counts the whole month. */
	static final int MONTH_COUNTS_UNTIL_DAY = 15;

	private static final BigDecimal TWO = BigDecimal.valueOf(2);

	private Proration() {
	}

	public static BigDecimal prorate(BigDecimal entitlement, LocalDate periodStart, LocalDate periodEnd,
			LocalDate joiningDate) {
		if (!joiningDate.isAfter(periodStart)) {
			return entitlement.setScale(2, RoundingMode.UNNECESSARY);
		}
		if (joiningDate.isAfter(periodEnd)) {
			return BigDecimal.ZERO.setScale(2);
		}
		long periodMonths = monthsInclusive(YearMonth.from(periodStart), YearMonth.from(periodEnd));
		long employedMonths = monthsInclusive(YearMonth.from(joiningDate), YearMonth.from(periodEnd));
		if (joiningDate.getDayOfMonth() > MONTH_COUNTS_UNTIL_DAY) {
			employedMonths--;
		}
		BigDecimal share = entitlement.multiply(BigDecimal.valueOf(employedMonths))
				.divide(BigDecimal.valueOf(periodMonths), 10, RoundingMode.HALF_UP);
		return roundToHalfDay(share);
	}

	public static BigDecimal roundToHalfDay(BigDecimal days) {
		return days.multiply(TWO).setScale(0, RoundingMode.HALF_UP).divide(TWO).setScale(2, RoundingMode.UNNECESSARY);
	}

	private static long monthsInclusive(YearMonth from, YearMonth to) {
		return ChronoUnit.MONTHS.between(from, to) + 1;
	}

}
