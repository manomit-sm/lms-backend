package com.bsolz.lms.notification.service;

import com.bsolz.lms.identity.api.IdentityApi;
import com.bsolz.lms.leave.api.LeaveApi;
import com.bsolz.lms.leave.api.LeaveSummary;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import com.bsolz.lms.notification.model.enums.NotificationType;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.settings.api.SettingsApi;
import com.bsolz.lms.shared.tenancy.TenantJobRunner;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Tells employees - and their managers - the day before approved leave starts. Sent from
 * {@value #SEND_FROM} on that day in the employee's location's timezone (else the tenant's); the job
 * runs hourly and sends whatever is due, so a missed run is caught up by the next one, and each leave
 * is announced once.
 */
@Service
@RequiredArgsConstructor
public class UpcomingLeaveNotifier {

	static final String UPCOMING_LEAVE = "upcoming-leave-notifications";

	static final LocalTime SEND_FROM = LocalTime.of(8, 0);

	private final TenantJobRunner jobRunner;

	private final LeaveApi leaveApi;

	private final LeaveDescriptions descriptions;

	private final OrganizationApi organizationApi;

	private final IdentityApi identityApi;

	private final SettingsApi settings;

	private final NotificationService notifications;

	private final Clock clock;

	@Scheduled(cron = "${lms.jobs.upcoming-leave-notifications}", zone = "UTC")
	@SchedulerLock(name = UPCOMING_LEAVE, lockAtMostFor = "PT30M")
	void notifyUpcomingLeave() {
		jobRunner.runForEachTenant(UPCOMING_LEAVE, tenant -> run(Instant.now(clock)));
	}

	/**
	 * Announces, for the bound tenant, approved leave starting tomorrow for every employee whose local
	 * time is past {@link #SEND_FROM}.
	 *
	 * @return the number of leave requests announced
	 */
	public int run(Instant now) {
		ZoneId tenantZone = settings.current().timezone();
		Map<UUID, ZoneId> locationZones = organizationApi.findLocationTimezones();
		Set<LocalDate> candidateDates = new HashSet<>();
		Set<ZoneId> zones = new HashSet<>(locationZones.values());
		zones.add(tenantZone);
		for (ZoneId zone : zones) {
			dueTomorrow(now, zone).ifPresent(candidateDates::add);
		}
		int announced = 0;
		for (LocalDate startDate : candidateDates) {
			List<LeaveSummary> starting = leaveApi.findLeavesStartingOn(startDate, Set.of(LeaveStatus.APPROVED));
			Map<UUID, EmployeeSummary> employees = new HashMap<>();
			organizationApi.findEmployees(starting.stream().map(LeaveSummary::employeeId).toList())
					.forEach(employee -> employees.put(employee.id(), employee));
			for (LeaveSummary leave : starting) {
				EmployeeSummary employee = employees.get(leave.employeeId());
				if (employee == null || employee.isExited()) {
					continue;
				}
				ZoneId zone = employee.locationId() == null ? tenantZone
						: locationZones.getOrDefault(employee.locationId(), tenantZone);
				if (dueTomorrow(now, zone).filter(startDate::equals).isPresent()) {
					announce(leave.id(), employee);
					announced++;
				}
			}
		}
		return announced;
	}

	private void announce(UUID leaveRequestId, EmployeeSummary employee) {
		descriptions.describe(leaveRequestId).ifPresent(leave -> {
			String key = "leave-starting:" + leaveRequestId;
			identityApi.findEnabledUserIdByEmployeeId(employee.id()).ifPresent(userId -> notifications.notify(
					Set.of(userId), NotificationType.LEAVE_STARTING, "Your leave starts tomorrow",
					"Your " + leave.what() + " starts tomorrow. Enjoy your time off.",
					NotificationPublisher.LEAVE_REQUEST, leaveRequestId, key));
			Optional.ofNullable(employee.reportingManagerId()).flatMap(identityApi::findEnabledUserIdByEmployeeId)
					.ifPresent(managerUserId -> notifications.notify(Set.of(managerUserId),
							NotificationType.LEAVE_STARTING, employee.fullName() + " is on leave from tomorrow",
							employee.fullName() + "'s " + leave.what() + " starts tomorrow.",
							NotificationPublisher.LEAVE_REQUEST, leaveRequestId, key));
		});
	}

	/** Tomorrow in the zone, once it is past the sending time there. */
	private static Optional<LocalDate> dueTomorrow(Instant now, ZoneId zone) {
		ZonedDateTime local = now.atZone(zone);
		return local.toLocalTime().isBefore(SEND_FROM) ? Optional.empty()
				: Optional.of(local.toLocalDate().plusDays(1));
	}

}
