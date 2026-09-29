package com.bsolz.lms.notification.service;

import com.bsolz.lms.leave.api.LeaveApi;
import com.bsolz.lms.leave.api.LeaveSummary;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.leavepolicy.api.LeaveTypeInfo;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.OrganizationApi;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Describes leave requests in notification texts, e.g. "Annual Leave on 6 - 8 Oct 2026 (3 days)". */
@Component
@RequiredArgsConstructor
class LeaveDescriptions {

	private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

	private final LeaveApi leaveApi;

	private final LeavePolicyApi policyApi;

	private final OrganizationApi organizationApi;

	Optional<Described> describe(UUID leaveRequestId) {
		return leaveApi.findLeave(leaveRequestId).map(leave -> new Described(leave,
				policyApi.findLeaveType(leave.leaveTypeId()).map(LeaveTypeInfo::name).orElse("Leave"),
				organizationApi.findEmployee(leave.employeeId()).map(EmployeeSummary::fullName).orElse("An employee")));
	}

	static String dates(LocalDate from, LocalDate to) {
		if (from.equals(to)) {
			return DAY_MONTH_YEAR.format(from);
		}
		if (from.getYear() == to.getYear()) {
			return from.getMonth() == to.getMonth()
					? from.getDayOfMonth() + " - " + DAY_MONTH_YEAR.format(to)
					: DAY_MONTH.format(from) + " - " + DAY_MONTH_YEAR.format(to);
		}
		return DAY_MONTH_YEAR.format(from) + " - " + DAY_MONTH_YEAR.format(to);
	}

	static String days(BigDecimal days) {
		BigDecimal plain = days.stripTrailingZeros();
		return plain.toPlainString() + (plain.compareTo(BigDecimal.ONE) <= 0 ? " day" : " days");
	}

	/** A leave request with the names needed to talk about it. */
	record Described(LeaveSummary leave, String leaveTypeName, String employeeName) {

		/** "Annual Leave on 6 - 8 Oct 2026 (3 days)" */
		String what() {
			return leaveTypeName + " on " + dates(leave.startDate(), leave.endDate()) + " (" + days(leave.totalDays())
					+ ")";
		}

	}

}
