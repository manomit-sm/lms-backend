package com.bsolz.lms.leave.domain;

import com.bsolz.lms.leave.entity.LeaveDay;
import com.bsolz.lms.leave.model.enums.DaySession;
import com.bsolz.lms.leave.model.enums.LeaveDayType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Works out how many leave days a request draws, day by day.
 * <ul>
 * <li>Working days count 1, or 0.5 for the half-day sessions allowed on the first and last day.</li>
 * <li>Weekends (days outside the work schedule) and day-off holidays count 0 - unless the policy's
 * sandwich rule applies and the day lies between two working days of the same request; then it counts
 * as a full day. Non-working days at either end of a request never count.</li>
 * </ul>
 */
public final class LeaveDayCalculator {

	private LeaveDayCalculator() {
	}

	/**
	 * @param startSession FULL_DAY or SECOND_HALF for multi-day requests; any session for a single day
	 * @param endSession FULL_DAY or FIRST_HALF for multi-day requests; equal to startSession for a single day
	 * @throws IllegalArgumentException for sessions that don't fit the dates (validate first with
	 * {@link #sessionsFit})
	 */
	public static Result calculate(LocalDate start, LocalDate end, DaySession startSession, DaySession endSession,
			Set<DayOfWeek> workingDays, Set<LocalDate> holidays, boolean sandwichRule) {
		if (!sessionsFit(start, end, startSession, endSession)) {
			throw new IllegalArgumentException("Sessions " + startSession + "/" + endSession + " don't fit the dates");
		}
		LocalDate firstWorking = null;
		LocalDate lastWorking = null;
		for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
			if (dayType(date, workingDays, holidays) == LeaveDayType.WORKING) {
				firstWorking = firstWorking == null ? date : firstWorking;
				lastWorking = date;
			}
		}
		List<LeaveDay> days = new ArrayList<>();
		BigDecimal total = BigDecimal.ZERO;
		for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
			DaySession session = date.equals(start) ? startSession : date.equals(end) ? endSession : DaySession.FULL_DAY;
			LeaveDayType type = dayType(date, workingDays, holidays);
			BigDecimal amount;
			if (type == LeaveDayType.WORKING) {
				amount = session.days();
			}
			else if (sandwichRule && firstWorking != null && date.isAfter(firstWorking) && date.isBefore(lastWorking)) {
				amount = BigDecimal.ONE;
				session = DaySession.FULL_DAY;
			}
			else {
				amount = BigDecimal.ZERO;
			}
			days.add(new LeaveDay(date, type, session, amount));
			total = total.add(amount);
		}
		return new Result(List.copyOf(days), total);
	}

	/** Half days only at the edges: a request can start in the afternoon and end at midday. */
	public static boolean sessionsFit(LocalDate start, LocalDate end, DaySession startSession, DaySession endSession) {
		if (start.equals(end)) {
			return startSession == endSession;
		}
		return startSession != DaySession.FIRST_HALF && endSession != DaySession.SECOND_HALF;
	}

	private static LeaveDayType dayType(LocalDate date, Set<DayOfWeek> workingDays, Set<LocalDate> holidays) {
		if (!workingDays.contains(date.getDayOfWeek())) {
			return LeaveDayType.WEEKEND;
		}
		return holidays.contains(date) ? LeaveDayType.HOLIDAY : LeaveDayType.WORKING;
	}

	public record Result(List<LeaveDay> days, BigDecimal totalDays) {
	}

}
