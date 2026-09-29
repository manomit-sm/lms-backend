package com.bsolz.lms.leave.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bsolz.lms.leave.entity.LeaveDay;
import com.bsolz.lms.leave.model.enums.DaySession;
import com.bsolz.lms.leave.model.enums.LeaveDayType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LeaveDayCalculatorTest {

	private static final Set<DayOfWeek> MON_FRI = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

	// 2026-03-02 is a Monday.
	private static final LocalDate MON = LocalDate.of(2026, 3, 2);

	@Test
	void countsWorkingDaysOnly() {
		// Thursday to next Tuesday: Thu, Fri, (Sat, Sun), Mon, Tue
		LeaveDayCalculator.Result result = calculate(MON.plusDays(3), MON.plusDays(8), Set.of(), false);

		assertThat(result.totalDays()).isEqualByComparingTo("4");
		assertThat(result.days()).extracting(LeaveDay::dayType).containsExactly(LeaveDayType.WORKING,
				LeaveDayType.WORKING, LeaveDayType.WEEKEND, LeaveDayType.WEEKEND, LeaveDayType.WORKING,
				LeaveDayType.WORKING);
	}

	@Test
	void holidaysAreNotLeaveDays() {
		LeaveDayCalculator.Result result = calculate(MON, MON.plusDays(4), Set.of(MON.plusDays(2)), false);

		assertThat(result.totalDays()).isEqualByComparingTo("4");
		assertThat(result.days().get(2)).isEqualTo(new LeaveDay(MON.plusDays(2), LeaveDayType.HOLIDAY,
				DaySession.FULL_DAY, BigDecimal.ZERO));
	}

	@Test
	void sandwichRuleCountsNonWorkingDaysBetweenLeaveDays() {
		Set<LocalDate> wednesdayHoliday = Set.of(MON.plusDays(2));
		// Fri to Tue: the weekend sits between leave days, so it counts.
		assertThat(calculate(MON.plusDays(4), MON.plusDays(8), Set.of(), true).totalDays()).isEqualByComparingTo("5");
		assertThat(calculate(MON, MON.plusDays(4), wednesdayHoliday, true).totalDays()).isEqualByComparingTo("5");
		// Sat to Tue: a weekend at the edge of a request never counts.
		assertThat(calculate(MON.plusDays(5), MON.plusDays(8), Set.of(), true).totalDays()).isEqualByComparingTo("2");
	}

	@Test
	void halfDaysAtEitherEnd() {
		assertThat(LeaveDayCalculator.calculate(MON, MON, DaySession.FIRST_HALF, DaySession.FIRST_HALF, MON_FRI,
				Set.of(), false).totalDays()).isEqualByComparingTo("0.5");
		// Mon afternoon to Wed midday: 0.5 + 1 + 0.5
		LeaveDayCalculator.Result result = LeaveDayCalculator.calculate(MON, MON.plusDays(2), DaySession.SECOND_HALF,
				DaySession.FIRST_HALF, MON_FRI, Set.of(), false);
		assertThat(result.totalDays()).isEqualByComparingTo("2");
		assertThat(result.days()).extracting(LeaveDay::session).containsExactly(DaySession.SECOND_HALF,
				DaySession.FULL_DAY, DaySession.FIRST_HALF);
		// A half day on a weekend draws nothing.
		assertThat(LeaveDayCalculator.calculate(MON.plusDays(5), MON.plusDays(5), DaySession.FIRST_HALF,
				DaySession.FIRST_HALF, MON_FRI, Set.of(), false).totalDays()).isZero();
	}

	@Test
	void followsTheEmployeesWorkSchedule() {
		Set<DayOfWeek> sixDays = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);
		assertThat(LeaveDayCalculator.calculate(MON, MON.plusDays(6), DaySession.FULL_DAY, DaySession.FULL_DAY, sixDays,
				Set.of(), false).totalDays()).isEqualByComparingTo("6");
	}

	@Test
	void sessionsMustFitTheDates() {
		assertThat(LeaveDayCalculator.sessionsFit(MON, MON, DaySession.SECOND_HALF, DaySession.SECOND_HALF)).isTrue();
		assertThat(LeaveDayCalculator.sessionsFit(MON, MON, DaySession.FIRST_HALF, DaySession.SECOND_HALF)).isFalse();
		assertThat(LeaveDayCalculator.sessionsFit(MON, MON.plusDays(1), DaySession.FIRST_HALF, DaySession.FULL_DAY))
				.isFalse();
		assertThat(LeaveDayCalculator.sessionsFit(MON, MON.plusDays(1), DaySession.FULL_DAY, DaySession.SECOND_HALF))
				.isFalse();
		assertThatThrownBy(() -> LeaveDayCalculator.calculate(MON, MON.plusDays(1), DaySession.FIRST_HALF,
				DaySession.FULL_DAY, MON_FRI, Set.of(), false)).isInstanceOf(IllegalArgumentException.class);
	}

	private static LeaveDayCalculator.Result calculate(LocalDate start, LocalDate end, Set<LocalDate> holidays,
			boolean sandwich) {
		return LeaveDayCalculator.calculate(start, end, DaySession.FULL_DAY, DaySession.FULL_DAY, MON_FRI, holidays,
				sandwich);
	}

}
