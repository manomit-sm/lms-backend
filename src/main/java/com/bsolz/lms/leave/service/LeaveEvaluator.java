package com.bsolz.lms.leave.service;

import com.bsolz.lms.balance.api.BalanceApi;
import com.bsolz.lms.balance.api.BalanceSnapshot;
import com.bsolz.lms.holiday.api.HolidayApi;
import com.bsolz.lms.holiday.api.HolidayInfo;
import com.bsolz.lms.leave.domain.LeaveDayCalculator;
import com.bsolz.lms.leave.domain.Violation;
import com.bsolz.lms.leave.entity.LeaveDay;
import com.bsolz.lms.leave.exception.LeaveErrorCode;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import com.bsolz.lms.leave.repository.LeaveRequestRepository;
import com.bsolz.lms.leavepolicy.api.LeavePeriodInfo;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.leavepolicy.api.LeaveTypeInfo;
import com.bsolz.lms.leavepolicy.api.ResolvedPolicy;
import com.bsolz.lms.leavepolicy.model.enums.LeavePeriodStatus;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.organization.model.enums.EmploymentStatus;
import com.bsolz.lms.settings.api.SettingsApi;
import com.bsolz.lms.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Checks an application against every leave rule and works out its days. Used by preview (shows all
 * violations) and submission (refuses on the first). Malformed input - dates or sessions that don't fit
 * together, an unknown leave type - fails straight away instead.
 */
@Component
@RequiredArgsConstructor
class LeaveEvaluator {

	static final int MAX_REQUEST_DAYS = 366;

	private final LeavePolicyApi policyApi;

	private final OrganizationApi organizationApi;

	private final HolidayApi holidayApi;

	private final BalanceApi balanceApi;

	private final SettingsApi settings;

	private final LeaveRequestRepository repository;

	Evaluation evaluate(EmployeeSummary employee, LeaveApplication application, int attachmentCount) {
		LocalDate start = application.startDate();
		LocalDate end = application.endDate();
		if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) >= MAX_REQUEST_DAYS) {
			throw new ApiException(LeaveErrorCode.INVALID_DATES,
					"The end date must be on or after the start date, within " + MAX_REQUEST_DAYS + " days");
		}
		if (!LeaveDayCalculator.sessionsFit(start, end, application.startSession(), application.endSession())) {
			throw new ApiException(LeaveErrorCode.INVALID_SESSIONS, start.equals(end)
					? "A single-day request has one session: FULL_DAY, FIRST_HALF or SECOND_HALF"
					: "A longer request may start with SECOND_HALF and end with FIRST_HALF, nothing else");
		}
		LeaveTypeInfo type = policyApi.findLeaveType(application.leaveTypeId())
				.orElseThrow(() -> new ApiException(LeaveErrorCode.LEAVE_TYPE_NOT_FOUND, "Leave type not found"));

		List<Violation> violations = new ArrayList<>();
		if (!type.active()) {
			violations.add(new Violation(LeaveErrorCode.LEAVE_TYPE_INACTIVE, type.name() + " is no longer offered"));
		}
		boolean halfDay = application.startSession().isHalf() || application.endSession().isHalf();
		if (halfDay && !type.halfDayAllowed()) {
			violations.add(new Violation(LeaveErrorCode.HALF_DAY_NOT_ALLOWED, type.name() + " is taken in whole days"));
		}
		if (employee.isExited()) {
			violations.add(new Violation(LeaveErrorCode.EMPLOYEE_EXITED, "The employee has left the organisation"));
		}
		if (start.isBefore(employee.joiningDate())) {
			violations.add(new Violation(LeaveErrorCode.BEFORE_JOINING,
					"Leave can't start before the joining date " + employee.joiningDate()));
		}

		LeavePeriodInfo period = policyApi.findPeriodContaining(start).orElse(null);
		if (period == null) {
			violations.add(new Violation(LeaveErrorCode.NO_LEAVE_PERIOD, "No leave period covers " + start));
		}
		else if (!period.contains(end)) {
			violations.add(new Violation(LeaveErrorCode.CROSSES_LEAVE_PERIOD, "Leave can't run past the end of leave period "
					+ period.name() + " (" + period.endDate() + "); split it into two requests"));
		}
		else if (period.status() == LeavePeriodStatus.CLOSED) {
			violations.add(new Violation(LeaveErrorCode.LEAVE_PERIOD_CLOSED, "Leave period " + period.name() + " is closed"));
		}

		ResolvedPolicy policy = policyApi.resolve(employee, type.id(), start).orElse(null);
		if (policy == null) {
			violations.add(new Violation(LeaveErrorCode.NOT_ELIGIBLE, "The employee is not eligible for " + type.name()));
		}

		Set<LocalDate> holidays = holidayApi.findHolidaysFor(employee, start, end).stream()
				.filter(HolidayInfo::isDayOff)
				.map(HolidayInfo::date)
				.collect(Collectors.toSet());
		LeaveDayCalculator.Result days = LeaveDayCalculator.calculate(start, end, application.startSession(),
				application.endSession(), organizationApi.findWorkingDays(employee.id()), holidays,
				policy != null && policy.sandwichRule());
		BigDecimal total = days.totalDays();
		if (total.signum() == 0) {
			violations.add(new Violation(LeaveErrorCode.NO_WORKING_DAYS, "The dates cover no working days"));
		}

		if (policy != null) {
			checkPolicy(policy, type, employee, start, total, attachmentCount, violations);
		}

		BigDecimal available = null;
		if (type.balanceTracked() && period != null && policy != null) {
			available = balanceApi.find(employee.id(), type.id(), period.id()).map(BalanceSnapshot::available)
					.orElse(BigDecimal.ZERO);
			if (total.compareTo(available.add(policy.negativeBalanceLimit())) > 0) {
				violations.add(new Violation(LeaveErrorCode.INSUFFICIENT_BALANCE,
						"Not enough " + type.name() + ": " + available + " day(s) available, " + total + " requested"));
			}
		}
		if (repository.existsOverlapping(employee.id(), start, end, LeaveStatus.LIVE)) {
			violations.add(new Violation(LeaveErrorCode.OVERLAPPING_LEAVE,
					"The dates overlap another pending or approved leave request"));
		}
		return new Evaluation(type, period, policy, days.days(), total, available, List.copyOf(violations));
	}

	private void checkPolicy(ResolvedPolicy policy, LeaveTypeInfo type, EmployeeSummary employee, LocalDate start,
			BigDecimal total, int attachmentCount, List<Violation> violations) {
		LocalDate today = settings.today();
		if (start.isBefore(today)) {
			long daysBack = ChronoUnit.DAYS.between(start, today);
			if (daysBack > policy.backdatingAllowedDays()) {
				violations.add(new Violation(LeaveErrorCode.BACKDATING_NOT_ALLOWED, policy.backdatingAllowedDays() == 0
						? type.name() + " can't be applied for in the past"
						: type.name() + " can start at most " + policy.backdatingAllowedDays() + " day(s) in the past"));
			}
		}
		else if (ChronoUnit.DAYS.between(today, start) < policy.minNoticeDays()) {
			violations.add(new Violation(LeaveErrorCode.INSUFFICIENT_NOTICE,
					type.name() + " needs " + policy.minNoticeDays() + " day(s) notice"));
		}
		if (policy.maxConsecutiveDays() != null && total.compareTo(BigDecimal.valueOf(policy.maxConsecutiveDays())) > 0) {
			violations.add(new Violation(LeaveErrorCode.MAX_CONSECUTIVE_DAYS_EXCEEDED,
					type.name() + " is limited to " + policy.maxConsecutiveDays() + " day(s) at a time"));
		}
		if (!policy.allowedDuringProbation() && onProbation(employee, start)) {
			violations.add(new Violation(LeaveErrorCode.NOT_ALLOWED_DURING_PROBATION,
					type.name() + " isn't available during probation"));
		}
		if (policy.attachmentRequiredAfterDays() != null && attachmentCount == 0
				&& total.compareTo(policy.attachmentRequiredAfterDays()) > 0) {
			violations.add(new Violation(LeaveErrorCode.ATTACHMENT_REQUIRED,
					type.name() + " of more than " + policy.attachmentRequiredAfterDays().stripTrailingZeros().toPlainString()
							+ " day(s) needs a supporting document"));
		}
	}

	private static boolean onProbation(EmployeeSummary employee, LocalDate date) {
		return employee.employmentStatus() == EmploymentStatus.PROBATION
				|| (employee.probationEndDate() != null && !date.isAfter(employee.probationEndDate()));
	}

	/**
	 * @param period null when no leave period covers the start
	 * @param policy null when the employee isn't eligible
	 * @param available the balance before this request; null when the type isn't balance-tracked
	 */
	record Evaluation(LeaveTypeInfo type, LeavePeriodInfo period, ResolvedPolicy policy, List<LeaveDay> days,
			BigDecimal totalDays, BigDecimal available, List<Violation> violations) {
	}

}
