package com.bsolz.lms.balance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.bsolz.lms.leavepolicy.model.enums.AccrualMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccrualScheduleTest {

	private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);

	private static final LocalDate DEC_31 = LocalDate.of(2026, 12, 31);

	@Test
	void monthlyInstallmentsAddUpToTheEntitlement() {
		List<AccrualSchedule.Installment> installments = AccrualSchedule.installments(AccrualMethod.MONTHLY,
				new BigDecimal("10"), JAN_1, DEC_31, LocalDate.of(2020, 5, 5));

		assertThat(installments).hasSize(12);
		assertThat(installments.getFirst()).isEqualTo(
				new AccrualSchedule.Installment(JAN_1, new BigDecimal("0.83"), "2026-01"));
		assertThat(installments.getLast().amount()).isEqualByComparingTo("0.87");
		assertThat(installments.getLast().date()).isEqualTo(LocalDate.of(2026, 12, 1));
		assertThat(total(installments)).isEqualByComparingTo("10");
	}

	@Test
	void quarterlyInstallmentsFallOnTheFirstDayOfEachQuarter() {
		List<AccrualSchedule.Installment> installments = AccrualSchedule.installments(AccrualMethod.QUARTERLY,
				new BigDecimal("18"), LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), LocalDate.of(2020, 1, 1));

		assertThat(installments).extracting(AccrualSchedule.Installment::date).containsExactly(
				LocalDate.of(2026, 4, 1), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2027, 1, 1));
		assertThat(installments).extracting(AccrualSchedule.Installment::label)
				.startsWith("2026-04 to 2026-06");
		assertThat(installments).allSatisfy(installment -> assertThat(installment.amount()).isEqualByComparingTo("4.5"));
	}

	@Test
	void joinersAccrueFromTheirFirstCountedMonth() {
		// Joining on the 15th counts that month; on the 16th it doesn't.
		assertThat(AccrualSchedule.installments(AccrualMethod.MONTHLY, new BigDecimal("12"), JAN_1, DEC_31,
				LocalDate.of(2026, 7, 15))).hasSize(6).first()
				.extracting(AccrualSchedule.Installment::date).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(AccrualSchedule.installments(AccrualMethod.MONTHLY, new BigDecimal("12"), JAN_1, DEC_31,
				LocalDate.of(2026, 7, 16))).hasSize(5);
		assertThat(AccrualSchedule.installments(AccrualMethod.MONTHLY, new BigDecimal("12"), JAN_1, DEC_31,
				LocalDate.of(2027, 1, 1))).isEmpty();
	}

	@Test
	void theJoiningQuarterIsProrated() {
		// Joined 10 May: May and June of the April-June quarter count, 2 of 3 months of 4 days.
		List<AccrualSchedule.Installment> installments = AccrualSchedule.installments(AccrualMethod.QUARTERLY,
				new BigDecimal("16"), JAN_1, DEC_31, LocalDate.of(2026, 5, 10));

		assertThat(installments).extracting(AccrualSchedule.Installment::amount)
				.usingElementComparator(BigDecimal::compareTo)
				.containsExactly(new BigDecimal("2.67"), new BigDecimal("4"), new BigDecimal("4"));
	}

	@Test
	void upfrontPoliciesHaveNoInstallments() {
		assertThat(AccrualSchedule.installments(AccrualMethod.UPFRONT, new BigDecimal("18"), JAN_1, DEC_31, JAN_1))
				.isEmpty();
	}

	private static BigDecimal total(List<AccrualSchedule.Installment> installments) {
		return installments.stream().map(AccrualSchedule.Installment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

}
