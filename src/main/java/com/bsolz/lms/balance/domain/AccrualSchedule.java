package com.bsolz.lms.balance.domain;

import com.bsolz.lms.leavepolicy.model.enums.AccrualMethod;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * When and how much an accruing policy credits during a leave period. The entitlement is split into
 * equal installments - one per month (MONTHLY) or per three months (QUARTERLY) of the period - credited
 * on the first day each installment covers; the last installment absorbs the rounding, so a full period
 * adds up to the entitlement exactly.
 * <p>
 * Someone joining part-way through gets only the months they are employed for, with the same rule as
 * {@link Proration}: the joining month counts if they join on or before the 15th. An installment covering
 * the joining month is reduced to the share of its months they are employed for.
 * <p>
 * Example: 10 days monthly over January-December: 0.83 on the first of each month, 0.87 on 1 December.
 */
public final class AccrualSchedule {

	private AccrualSchedule() {
	}

	/** Installments with a positive amount, in date order. UPFRONT policies have none. */
	public static List<Installment> installments(AccrualMethod method, BigDecimal entitlement, LocalDate periodStart,
			LocalDate periodEnd, LocalDate joiningDate) {
		int monthsPerInstallment = switch (method) {
			case UPFRONT -> 0;
			case MONTHLY -> 1;
			case QUARTERLY -> 3;
		};
		if (monthsPerInstallment == 0 || entitlement.signum() <= 0) {
			return List.of();
		}
		YearMonth first = YearMonth.from(periodStart);
		YearMonth last = YearMonth.from(periodEnd);
		int periodMonths = (int) (first.until(last, ChronoUnit.MONTHS) + 1);
		int count = (periodMonths + monthsPerInstallment - 1) / monthsPerInstallment;
		BigDecimal base = entitlement.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
		BigDecimal lastAmount = entitlement.subtract(base.multiply(BigDecimal.valueOf(count - 1)));
		YearMonth firstCountedMonth = firstEmployedMonth(joiningDate);

		List<Installment> installments = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			YearMonth from = first.plusMonths((long) index * monthsPerInstallment);
			YearMonth to = min(from.plusMonths(monthsPerInstallment - 1), last);
			int months = (int) (from.until(to, ChronoUnit.MONTHS) + 1);
			int employedMonths = firstCountedMonth.isAfter(to) ? 0
					: months - (int) Math.max(0, from.until(firstCountedMonth, ChronoUnit.MONTHS));
			if (employedMonths <= 0) {
				continue;
			}
			BigDecimal full = index == count - 1 ? lastAmount : base;
			BigDecimal amount = employedMonths == months ? full
					: full.multiply(BigDecimal.valueOf(employedMonths))
							.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
			if (amount.signum() > 0) {
				LocalDate date = from.atDay(1).isBefore(periodStart) ? periodStart : from.atDay(1);
				installments.add(new Installment(date, amount, label(from, to)));
			}
		}
		return installments;
	}

	private static YearMonth firstEmployedMonth(LocalDate joiningDate) {
		YearMonth joined = YearMonth.from(joiningDate);
		return joiningDate.getDayOfMonth() > Proration.MONTH_COUNTS_UNTIL_DAY ? joined.plusMonths(1) : joined;
	}

	private static YearMonth min(YearMonth a, YearMonth b) {
		return a.isBefore(b) ? a : b;
	}

	private static String label(YearMonth from, YearMonth to) {
		return from.equals(to) ? from.toString() : from + " to " + to;
	}

	/**
	 * @param date when it is credited
	 * @param label the months it covers, e.g. {@code 2026-03} or {@code 2026-04 to 2026-06}
	 */
	public record Installment(LocalDate date, BigDecimal amount, String label) {
	}

}
