package com.bsolz.lms.notification.service;

import com.bsolz.lms.approval.api.ApprovalTaskAssigned;
import com.bsolz.lms.approval.api.ApprovalTaskEscalated;
import com.bsolz.lms.approval.api.ApprovalTaskReminded;
import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import com.bsolz.lms.balance.api.BalanceAdjusted;
import com.bsolz.lms.identity.api.IdentityApi;
import com.bsolz.lms.leave.api.LeaveRequestStatusChanged;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.leavepolicy.api.LeaveTypeInfo;
import com.bsolz.lms.notification.model.enums.NotificationType;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.shared.tenancy.TenantContext;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns domain events into notifications: who hears about what, and what it says. Runs with the event's
 * tenant bound. Nobody is notified about their own action.
 */
@Service
@Transactional
@RequiredArgsConstructor
class NotificationPublisher {

	static final String LEAVE_REQUEST = "LEAVE_REQUEST";

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH);

	private final NotificationService notifications;

	private final LeaveDescriptions descriptions;

	private final IdentityApi identityApi;

	private final OrganizationApi organizationApi;

	private final LeavePolicyApi policyApi;

	void leaveStatusChanged(LeaveRequestStatusChanged event) {
		Optional<LeaveDescriptions.Described> found = descriptions.describe(event.leaveRequestId());
		if (found.isEmpty()) {
			return;
		}
		LeaveDescriptions.Described leave = found.get();
		String actor = event.actorUserId() == null ? null
				: identityApi.findDisplayNames(Set.of(event.actorUserId())).getOrDefault(event.actorUserId(), "someone");
		String by = actor == null ? " automatically" : " by " + actor;
		String comment = event.comment() == null || event.comment().isBlank() ? "" : ": " + event.comment();
		Optional<UUID> requester = identityApi.findEnabledUserIdByEmployeeId(event.employeeId());
		String key = "leave:" + event.leaveRequestId() + ":" + event.action() + ":" + event.occurredAt();
		switch (event.action()) {
			case APPROVE -> toUser(requester, event, NotificationType.LEAVE_APPROVED, "Leave approved",
					"Your " + leave.what() + " was approved" + by + ".", key);
			case REJECT -> toUser(requester, event, NotificationType.LEAVE_REJECTED, "Leave rejected",
					"Your " + leave.what() + " was rejected" + by + comment + ".", key);
			case APPROVE_CANCELLATION -> toUser(requester, event, NotificationType.CANCELLATION_APPROVED,
					"Cancellation approved", "Cancelling your " + leave.what() + " was approved" + by
							+ ". The days are back in your balance.", key);
			case REJECT_CANCELLATION -> toUser(requester, event, NotificationType.CANCELLATION_REJECTED,
					"Cancellation rejected", "Cancelling your " + leave.what() + " was rejected" + by + comment
							+ ". The leave stays approved.", key);
			case CANCEL -> {
				if (requester.isPresent() && !requester.get().equals(event.actorUserId())) {
					toUser(requester, event, NotificationType.LEAVE_CANCELLED, "Leave cancelled",
							"Your " + leave.what() + " was cancelled" + by + comment + ".", key);
				}
				else {
					toUser(managerUserOf(event.employeeId()), event, NotificationType.LEAVE_CANCELLED, "Leave cancelled",
							leave.employeeName() + " cancelled their " + leave.what() + comment + ".", key);
				}
			}
			// Submissions and cancellation requests reach the approvers through ApprovalTaskAssigned; a
			// withdrawal needs no one's attention.
			case SUBMIT, REQUEST_CANCELLATION, WITHDRAW -> {
			}
		}
	}

	void approvalTaskAssigned(ApprovalTaskAssigned event) {
		descriptions.describe(event.subjectId()).ifPresent(leave -> {
			boolean cancellation = event.subjectType() == ApprovalSubjectType.LEAVE_CANCELLATION;
			notifications.notify(event.assigneeUserIds(), NotificationType.APPROVAL_REQUESTED,
					cancellation ? "Cancellation to approve" : "Leave request to approve",
					cancellation ? leave.employeeName() + " asks to cancel their " + leave.what() + "."
							: leave.employeeName() + " requested " + leave.what() + ".",
					LEAVE_REQUEST, event.subjectId(), "task:" + event.taskId() + ":assigned");
		});
	}

	void approvalTaskReminded(ApprovalTaskReminded event) {
		descriptions.describe(event.subjectId()).ifPresent(leave -> notifications.notify(event.assigneeUserIds(),
				NotificationType.APPROVAL_REMINDER, "Reminder: approval waiting",
				leave.employeeName() + "'s " + leave.what() + " has been waiting for your decision since "
						+ since(event.pendingSince()) + ".",
				LEAVE_REQUEST, event.subjectId(), "task:" + event.taskId() + ":reminded:" + event.remindedAt()));
	}

	void approvalTaskEscalated(ApprovalTaskEscalated event) {
		descriptions.describe(event.subjectId()).ifPresent(leave -> notifications.notify(event.addedUserIds(),
				NotificationType.APPROVAL_ESCALATED, "Approval escalated to you",
				leave.employeeName() + "'s " + leave.what() + " has been waiting for a decision since "
						+ since(event.pendingSince()) + ". You can now approve or reject it.",
				LEAVE_REQUEST, event.subjectId(), "task:" + event.taskId() + ":escalated"));
	}

	void balanceAdjusted(BalanceAdjusted event) {
		Optional<UUID> employeeUser = identityApi.findEnabledUserIdByEmployeeId(event.employeeId());
		if (employeeUser.isEmpty() || employeeUser.get().equals(event.actorUserId())) {
			return;
		}
		String type = policyApi.findLeaveType(event.leaveTypeId()).map(LeaveTypeInfo::name).orElse("leave");
		String change = (event.amount().signum() > 0 ? "+" : "-") + LeaveDescriptions.days(event.amount().abs());
		notifications.notify(Set.of(employeeUser.get()), NotificationType.BALANCE_ADJUSTED, "Leave balance adjusted",
				"Your " + type + " balance was adjusted by " + change + ": " + event.reason() + ".", "LEAVE_BALANCE",
				null, "balance-adjustment:" + event.transactionId());
	}

	/** In the tenant's default timezone: the recipients may be anywhere. */
	private static String since(Instant instant) {
		return WHEN.format(instant.atZone(TenantContext.require().timezone()));
	}

	private Optional<UUID> managerUserOf(UUID employeeId) {
		return organizationApi.findEmployee(employeeId).map(EmployeeSummary::reportingManagerId)
				.flatMap(identityApi::findEnabledUserIdByEmployeeId);
	}

	private void toUser(Optional<UUID> userId, LeaveRequestStatusChanged event, NotificationType type, String title,
			String message, String key) {
		userId.filter(id -> !id.equals(event.actorUserId())).ifPresent(id -> notifications.notify(Set.of(id), type,
				title, message, LEAVE_REQUEST, event.leaveRequestId(), key));
	}

}
