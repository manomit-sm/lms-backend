package com.bsolz.lms.calendar.service;

import com.bsolz.lms.calendar.exception.CalendarErrorCode;
import com.bsolz.lms.calendar.model.enums.AvailabilityStatus;
import com.bsolz.lms.calendar.model.enums.CalendarScope;
import com.bsolz.lms.calendar.web.dto.AvailabilityResponse;
import com.bsolz.lms.calendar.web.dto.CalendarResponse;
import com.bsolz.lms.holiday.api.HolidayApi;
import com.bsolz.lms.holiday.api.HolidayInfo;
import com.bsolz.lms.leave.api.LeaveApi;
import com.bsolz.lms.leave.api.LeaveSummary;
import com.bsolz.lms.leave.model.enums.DaySession;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.leavepolicy.api.LeaveTypeInfo;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.EmployeeVisibility;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.security.CurrentUser;
import com.bsolz.lms.shared.security.DataScope;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calendar views for the current user. Members the caller may see as employees (themselves, their
 * reporting line, or everyone - {@link EmployeeVisibility}) are shown in detail; for other members only
 * approved leave is shown, without its type, so a colleague's calendar never reveals e.g. sick leave.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CalendarService {

	/** Longest range for one calendar request: about three months. */
	static final int MAX_RANGE_DAYS = 92;

	private static final Set<LeaveStatus> DETAILED_STATUSES = Set.of(LeaveStatus.PENDING, LeaveStatus.APPROVED,
			LeaveStatus.CANCELLATION_PENDING);

	/** Approved leave, including leave whose cancellation awaits approval (it still stands until then). */
	private static final Set<LeaveStatus> APPROVED_STATUSES = Set.of(LeaveStatus.APPROVED,
			LeaveStatus.CANCELLATION_PENDING);

	private final OrganizationApi organizationApi;

	private final EmployeeVisibility visibility;

	private final LeaveApi leaveApi;

	private final LeavePolicyApi policyApi;

	private final HolidayApi holidayApi;

	public CalendarResponse calendar(CalendarScope scope, UUID departmentId, LocalDate from, LocalDate to) {
		if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
			throw new ApiException(CalendarErrorCode.INVALID_RANGE,
					"to must not be before from, and the range may cover at most " + MAX_RANGE_DAYS + " days");
		}
		EmployeeSummary me = currentEmployee();
		List<EmployeeSummary> members = members(scope, me, departmentId);
		Set<UUID> detailed = detailedMembers(me, members);
		Map<UUID, Optional<LeaveTypeInfo>> types = new HashMap<>();

		List<CalendarResponse.Leave> leaves = new ArrayList<>();
		for (LeaveSummary leave : leaveApi.findLeaves(ids(members), from, to, DETAILED_STATUSES)) {
			boolean inDetail = detailed.contains(leave.employeeId());
			if (!inDetail && !APPROVED_STATUSES.contains(leave.status())) {
				continue;
			}
			Optional<LeaveTypeInfo> type = types.computeIfAbsent(leave.leaveTypeId(), policyApi::findLeaveType);
			boolean timeOff = type.map(LeaveTypeInfo::timeOff).orElse(true);
			leaves.add(inDetail
					? new CalendarResponse.Leave(leave.id(), leave.employeeId(), type.map(CalendarService::ref).orElse(null),
							timeOff, leave.startDate(), leave.endDate(), leave.startSession(), leave.endSession(),
							leave.totalDays(), leave.status())
					: new CalendarResponse.Leave(null, leave.employeeId(), null, timeOff, leave.startDate(),
							leave.endDate(), leave.startSession(), leave.endSession(), null, leave.status()));
		}
		List<CalendarResponse.Holiday> holidays = holidayApi.findHolidaysFor(me, from, to).stream()
				.map(holiday -> new CalendarResponse.Holiday(holiday.id(), holiday.name(), holiday.date(),
						holiday.type()))
				.toList();
		return new CalendarResponse(scope, from, to, members.stream()
				.map(member -> new CalendarResponse.Member(member.id(), member.fullName(), member.departmentId(),
						detailed.contains(member.id())))
				.toList(), leaves, holidays);
	}

	public AvailabilityResponse availability(CalendarScope scope, UUID departmentId, LocalDate date) {
		EmployeeSummary me = currentEmployee();
		List<EmployeeSummary> members = members(scope, me, departmentId);
		Set<UUID> detailed = detailedMembers(me, members);
		Map<UUID, List<LeaveSummary>> leavesByEmployee = new HashMap<>();
		leaveApi.findLeaves(ids(members), date, date, APPROVED_STATUSES)
				.forEach(leave -> leavesByEmployee.computeIfAbsent(leave.employeeId(), id -> new ArrayList<>()).add(leave));
		Map<UUID, Optional<LeaveTypeInfo>> types = new HashMap<>();

		Map<AvailabilityStatus, Integer> counts = new EnumMap<>(AvailabilityStatus.class);
		for (AvailabilityStatus status : AvailabilityStatus.values()) {
			counts.put(status, 0);
		}
		List<AvailabilityResponse.EmployeeAvailability> employees = new ArrayList<>();
		for (EmployeeSummary member : members) {
			AvailabilityResponse.EmployeeAvailability availability = availabilityOf(member, date,
					leavesByEmployee.getOrDefault(member.id(), List.of()), detailed.contains(member.id()), types);
			counts.merge(availability.status(), 1, Integer::sum);
			employees.add(availability);
		}
		return new AvailabilityResponse(date, scope, counts, employees);
	}

	private AvailabilityResponse.EmployeeAvailability availabilityOf(EmployeeSummary member, LocalDate date,
			List<LeaveSummary> leaves, boolean inDetail, Map<UUID, Optional<LeaveTypeInfo>> types) {
		if (!organizationApi.findWorkingDays(member.id()).contains(date.getDayOfWeek())) {
			return status(member, AvailabilityStatus.NON_WORKING_DAY, null, null, null);
		}
		Optional<HolidayInfo> holiday = holidayApi.findHolidaysFor(member, date, date).stream()
				.filter(HolidayInfo::isDayOff).findFirst();
		if (holiday.isPresent()) {
			return status(member, AvailabilityStatus.HOLIDAY, null, null, holiday.get().name());
		}
		Set<DaySession> away = new HashSet<>();
		LeaveTypeInfo awayType = null;
		LeaveTypeInfo remoteType = null;
		boolean remote = false;
		for (LeaveSummary leave : leaves) {
			Optional<LeaveSummary.Day> day = leave.dayOn(date);
			if (day.isEmpty()) {
				continue;
			}
			Optional<LeaveTypeInfo> type = types.computeIfAbsent(leave.leaveTypeId(), policyApi::findLeaveType);
			if (type.map(LeaveTypeInfo::timeOff).orElse(true)) {
				away.add(day.get().session());
				awayType = type.orElse(awayType);
			}
			else {
				remote = true;
				remoteType = type.orElse(remoteType);
			}
		}
		boolean fullDay = away.contains(DaySession.FULL_DAY)
				|| away.containsAll(Set.of(DaySession.FIRST_HALF, DaySession.SECOND_HALF));
		if (fullDay) {
			return status(member, AvailabilityStatus.ON_LEAVE, null, inDetail ? awayType : null, null);
		}
		if (!away.isEmpty()) {
			return status(member, AvailabilityStatus.HALF_DAY_LEAVE, away.iterator().next(),
					inDetail ? awayType : null, null);
		}
		if (remote) {
			return status(member, AvailabilityStatus.REMOTE, null, inDetail ? remoteType : null, null);
		}
		return status(member, AvailabilityStatus.AVAILABLE, null, null, null);
	}

	private static AvailabilityResponse.EmployeeAvailability status(EmployeeSummary member, AvailabilityStatus status,
			DaySession session, LeaveTypeInfo type, String holidayName) {
		return new AvailabilityResponse.EmployeeAvailability(member.id(), member.fullName(), status, session,
				type == null ? null : ref(type), holidayName);
	}

	/** The scope's current employees, sorted by name. */
	private List<EmployeeSummary> members(CalendarScope scope, EmployeeSummary me, UUID departmentId) {
		Map<UUID, EmployeeSummary> members = new LinkedHashMap<>();
		members.put(me.id(), me);
		switch (scope) {
			case ME -> {
			}
			case TEAM -> {
				organizationApi.findCurrentDirectReports(me.id()).forEach(report -> members.put(report.id(), report));
				if (me.reportingManagerId() != null) {
					organizationApi.findEmployee(me.reportingManagerId()).filter(manager -> !manager.isExited())
							.ifPresent(manager -> members.put(manager.id(), manager));
					organizationApi.findCurrentDirectReports(me.reportingManagerId())
							.forEach(peer -> members.put(peer.id(), peer));
				}
			}
			case DEPARTMENT -> {
				UUID department = departmentId != null ? departmentId : me.departmentId();
				if (!department.equals(me.departmentId()) && visibility.currentScope() != DataScope.TENANT) {
					throw new ApiException(CalendarErrorCode.DEPARTMENT_NOT_VISIBLE,
							"You can only see your own department's calendar");
				}
				if (!department.equals(me.departmentId())) {
					members.remove(me.id());
				}
				organizationApi.findCurrentEmployeesInDepartment(department)
						.forEach(employee -> members.put(employee.id(), employee));
			}
		}
		return members.values().stream()
				.sorted(Comparator.comparing(EmployeeSummary::firstName, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(EmployeeSummary::lastName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	/** Members whose leave the caller may see in detail, worked out once rather than per member. */
	private Set<UUID> detailedMembers(EmployeeSummary me, List<EmployeeSummary> members) {
		Set<UUID> ids = ids(members);
		return switch (visibility.currentScope()) {
			case TENANT -> ids;
			case TEAM -> {
				Set<UUID> line = new HashSet<>(organizationApi.findReportingLine(me.id()));
				line.add(me.id());
				line.retainAll(ids);
				yield line;
			}
			case SELF -> ids.contains(me.id()) ? Set.of(me.id()) : Set.of();
		};
	}

	private EmployeeSummary currentEmployee() {
		UUID employeeId = CurrentUser.require().employeeId();
		return Optional.ofNullable(employeeId).flatMap(organizationApi::findEmployee)
				.orElseThrow(() -> new ApiException(CalendarErrorCode.NOT_LINKED_TO_EMPLOYEE,
						"Your user is not linked to an employee record"));
	}

	private static Set<UUID> ids(List<EmployeeSummary> employees) {
		return employees.stream().map(EmployeeSummary::id).collect(Collectors.toSet());
	}

	private static CalendarResponse.LeaveTypeRef ref(LeaveTypeInfo type) {
		return new CalendarResponse.LeaveTypeRef(type.id(), type.code(), type.name(), type.color());
	}

}
