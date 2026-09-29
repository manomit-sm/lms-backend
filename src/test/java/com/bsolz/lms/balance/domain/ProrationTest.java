package com.bsolz.lms.balance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProrationTest {

	private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);

	private static final LocalDate DEC_31 = LocalDate.of(2026, 12, 31);

	@ParameterizedTest(name = "{0} days, joining {1} -> {2}")
	@CsvSource({
			// joined before or on the first day: everything
			"18, 2025-06-01, 18.00",
			"18, 2026-01-01, 18.00",
			// the joining month counts up to the 15th
			"18, 2026-01-15, 18.00",
			"18, 2026-01-16, 16.50",
			"18, 2026-07-01, 9.00",
			"18, 2026-07-15, 9.00",
			"18, 2026-07-20, 7.50",
			"18, 2026-12-10, 1.50",
			"18, 2026-12-16, 0.00",
			// rounded to the nearest half day: 10 x 5/12 = 4.17 -> 4.0, 10 x 7/12 = 5.83 -> 6.0, 15 x 5/12 = 6.25 -> 6.5
			"10, 2026-08-01, 4.00",
			"10, 2026-06-01, 6.00",
			"15, 2026-08-01, 6.50",
			// after the period: nothing
			"18, 2027-01-01, 0.00" })
	void proratesByRemainingMonths(BigDecimal entitlement, LocalDate joiningDate, BigDecimal expected) {
		assertThat(Proration.prorate(entitlement, JAN_1, DEC_31, joiningDate)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({ "4.24, 4.00", "4.25, 4.50", "4.74, 4.50", "4.75, 5.00", "0, 0.00" })
	void roundsToNearestHalfDay(BigDecimal days, BigDecimal expected) {
		assertThat(Proration.roundToHalfDay(days)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "leave year April-March, joining {0} -> {1}")
	@CsvSource({ "2026-04-01, 12.00", "2026-10-01, 6.00", "2027-03-01, 1.00" })
	void worksForPeriodsSpanningTwoYears(LocalDate joiningDate, BigDecimal expected) {
		assertThat(Proration.prorate(new BigDecimal("12"), LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31),
				joiningDate)).isEqualTo(expected);
	}

}
