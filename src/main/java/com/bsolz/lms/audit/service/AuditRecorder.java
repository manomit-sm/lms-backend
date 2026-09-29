package com.bsolz.lms.audit.service;

import com.bsolz.lms.approval.api.ApprovalTaskDecided;
import com.bsolz.lms.approval.api.ApprovalTaskEscalated;
import com.bsolz.lms.approval.model.enums.ApprovalStatus;
import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import com.bsolz.lms.audit.model.enums.AuditAction;
import com.bsolz.lms.audit.model.enums.AuditEntityType;
import com.bsolz.lms.audit.repository.ActivityLogRepository;
import com.bsolz.lms.balance.api.BalanceAdjusted;
import com.bsolz.lms.balance.api.LeavePeriodRolledOver;
import com.bsolz.lms.identity.api.IdentityApi;
import com.bsolz.lms.identity.api.UserSummary;
import com.bsolz.lms.leave.api.LeaveApi;
import com.bsolz.lms.leave.api.LeaveRequestStatusChanged;
import com.bsolz.lms.leave.api.LeaveSummary;
import com.bsolz.lms.leave.model.enums.LeaveAction;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.leavepolicy.api.LeaveTypeInfo;
import com.bsolz.lms.organization.api.EmployeeCreated;
import com.bsolz.lms.organization.api.EmployeeExited;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.OrganizationApi;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the audit trail from domain events, with the tenant of the event bound. Names are resolved now
 * and stored, so the log keeps saying who did what even after people are renamed or leave. Each entry
 * is keyed by its event, so a redelivered event is recorded once.
 */
@Service
@Transactional
@RequiredArgsConstructor
class AuditRecorder {

	private static final Map<LeaveAction, AuditAction> LEAVE_ACTIONS = Map.of(LeaveAction.SUBMIT,
			AuditAction.LEAVE_SUBMITTED, LeaveAction.APPROVE, AuditAction.LEAVE_APPROVED, LeaveAction.REJECT,
			AuditAction.LEAVE_REJECTED, LeaveAction.WITHDRAW, AuditAction.LEAVE_WITHDRAWN, LeaveAction.CANCEL,
			AuditAction.LEAVE_CANCELLED, LeaveAction.REQUEST_CANCELLATION, AuditAction.LEAVE_CANCELLATION_REQUESTED,
			LeaveAction.APPROVE_CANCELLATION, AuditAction.LEAVE_CANCELLATION_APPROVED, LeaveAction.REJECT_CANCELLATION,
			AuditAction.LEAVE_CANCELLATION_REJECTED);

	private final ActivityLogRepository repository;

	private final IdentityApi identityApi;

	private final OrganizationApi organizationApi;

	private final LeavePolicyApi policyApi;

	private final LeaveApi leaveApi;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	void employeeCreated(EmployeeCreated event) {
		Actor actor = actor(event.actorUserId());
		String name = employeeName(event.employeeId());
		record(Instant.now(clock), actor, AuditAction.EMPLOYEE_CREATED, AuditEntityType.EMPLOYEE, event.employeeId(),
				event.employeeId(), name, actor.name() + " added " + name + " (" + event.email() + ")",
				details("email", event.email()), "employee-created:" + event.employeeId());
	}

	void employeeExited(EmployeeExited event) {
		Actor actor = actor(event.actorUserId());
		String name = employeeName(event.employeeId());
		record(Instant.now(clock), actor, AuditAction.EMPLOYEE_EXITED, AuditEntityType.EMPLOYEE, event.employeeId(),
				event.employeeId(), name, actor.name() + " recorded that " + name + " leaves on " + event.exitDate(),
				details("exitDate", event.exitDate()), "employee-exited:" + event.employeeId());
	}

	void leaveStatusChanged(LeaveRequestStatusChanged event) {
		Actor actor = actor(event.actorUserId());
		String employee = employeeName(event.employeeId());
		String whose = actor.isEmployee(event.employeeId()) ? "their" : employee + "'s";
		String what = leaveDescription(event.leaveRequestId(), event.leaveTypeId());
		String summary = switch (event.action()) {
			case SUBMIT -> actor.name() + " requested " + what + (actor.isEmployee(event.employeeId()) ? ""
					: " for " + employee);
			case APPROVE -> actor.userId() == null ? employee + "'s " + what + " was approved automatically"
					: actor.name() + " approved " + whose + " " + what;
			case REJECT -> actor.name() + " rejected " + whose + " " + what;
			case WITHDRAW -> actor.name() + " withdrew " + whose + " " + what;
			case CANCEL -> actor.name() + " cancelled " + whose + " " + what;
			case REQUEST_CANCELLATION -> actor.name() + " asked to cancel " + whose + " " + what;
			case APPROVE_CANCELLATION -> actor.userId() == null
					? "Cancelling " + employee + "'s " + what + " was approved automatically"
					: actor.name() + " approved cancelling " + whose + " " + what;
			case REJECT_CANCELLATION -> actor.name() + " rejected cancelling " + whose + " " + what;
		};
		Map<String, Object> details = details("from", event.from(), "to", event.to(), "leaveTypeId",
				event.leaveTypeId(), "comment", event.comment());
		record(event.occurredAt(), actor, LEAVE_ACTIONS.get(event.action()), AuditEntityType.LEAVE_REQUEST,
				event.leaveRequestId(), event.employeeId(), employee, summary, details,
				"leave:" + event.leaveRequestId() + ":" + event.action() + ":" + event.occurredAt());
	}

	/** Only intermediate steps: the final decision is recorded through the leave request's status change. */
	void approvalTaskDecided(ApprovalTaskDecided event) {
		if (event.approvalStatus() != ApprovalStatus.PENDING || !isLeave(event.subjectType())) {
			return;
		}
		Actor actor = actor(event.decidedByUserId());
		String employee = employeeName(event.requesterEmployeeId());
		String what = leaveDescription(event.subjectId(), null);
		String subject = (event.subjectType() == ApprovalSubjectType.LEAVE_CANCELLATION ? "cancelling " : "")
				+ employee + "'s " + what;
		String summary = actor.userId() == null
				? "Step " + event.stepOrder() + " of approving " + subject + " was approved automatically"
				: actor.name() + " approved step " + event.stepOrder() + " of " + subject;
		record(event.decidedAt(), actor, AuditAction.APPROVAL_STEP_APPROVED, AuditEntityType.LEAVE_REQUEST,
				event.subjectId(), event.requesterEmployeeId(), employee, summary,
				details("approvalId", event.approvalId(), "stepOrder", event.stepOrder(), "comment", event.comment()),
				"approval-task-decided:" + event.taskId());
	}

	void approvalTaskEscalated(ApprovalTaskEscalated event) {
		if (!isLeave(event.subjectType())) {
			return;
		}
		String employee = employeeName(event.requesterEmployeeId());
		Map<UUID, String> added = identityApi.findDisplayNames(event.addedUserIds());
		record(event.escalatedAt(), Actor.SYSTEM, AuditAction.APPROVAL_ESCALATED, AuditEntityType.LEAVE_REQUEST,
				event.subjectId(), event.requesterEmployeeId(), employee,
				"Approving " + employee + "'s " + leaveDescription(event.subjectId(), null) + " was escalated to "
						+ String.join(", ", added.values().stream().sorted().toList()),
				details("approvalId", event.approvalId(), "addedUserIds", event.addedUserIds()),
				"approval-task-escalated:" + event.taskId());
	}

	void balanceAdjusted(BalanceAdjusted event) {
		Actor actor = actor(event.actorUserId());
		String employee = employeeName(event.employeeId());
		String type = policyApi.findLeaveType(event.leaveTypeId()).map(LeaveTypeInfo::name).orElse("leave");
		String amount = (event.amount().signum() > 0 ? "+" : "") + event.amount().stripTrailingZeros().toPlainString();
		record(Instant.now(clock), actor, AuditAction.BALANCE_ADJUSTED, AuditEntityType.LEAVE_BALANCE_TRANSACTION,
				event.transactionId(), event.employeeId(), employee,
				actor.name() + " adjusted " + employee + "'s " + type + " balance by " + amount + " day(s)",
				details("leaveTypeId", event.leaveTypeId(), "leavePeriodId", event.leavePeriodId(), "amount",
						event.amount(), "reason", event.reason()),
				"balance-adjustment:" + event.transactionId());
	}

	void leavePeriodRolledOver(LeavePeriodRolledOver event) {
		record(Instant.now(clock), Actor.SYSTEM, AuditAction.LEAVE_PERIOD_CLOSED, AuditEntityType.LEAVE_PERIOD,
				event.leavePeriodId(), null, null,
				"Leave period " + event.leavePeriodName() + " closed: " + plain(event.carriedForwardDays())
						+ " day(s) carried forward, " + plain(event.lapsedDays()) + " lapsed",
				details("nextLeavePeriodId", event.nextLeavePeriodId(), "balances", event.balances(),
						"carriedForwardDays", event.carriedForwardDays(), "lapsedDays", event.lapsedDays()),
				"leave-period-closed:" + event.leavePeriodId());
	}

	private void record(Instant occurredAt, Actor actor, AuditAction action, AuditEntityType entityType, UUID entityId,
			UUID employeeId, String employeeName, String summary, Map<String, Object> details, String eventKey) {
		repository.insertIfAbsent(occurredAt, actor.userId(), actor.userId() == null ? null : actor.name(),
				action.name(), entityType.name(), entityId, employeeId, employeeName, truncate(summary),
				jsonMapper.writeValueAsString(details), eventKey);
	}

	private Actor actor(UUID userId) {
		if (userId == null) {
			return Actor.SYSTEM;
		}
		Optional<UserSummary> user = identityApi.findUsers(Set.of(userId)).stream().findFirst();
		return new Actor(userId, identityApi.findDisplayNames(Set.of(userId)).getOrDefault(userId, "Unknown user"),
				user.map(UserSummary::employeeId).orElse(null));
	}

	private String employeeName(UUID employeeId) {
		return organizationApi.findEmployee(employeeId).map(EmployeeSummary::fullName).orElse("Unknown employee");
	}

	/** "Annual Leave (2026-10-06 to 2026-10-08, 3 days)" */
	private String leaveDescription(UUID leaveRequestId, UUID leaveTypeId) {
		Optional<LeaveSummary> leave = leaveApi.findLeave(leaveRequestId);
		UUID typeId = leave.map(LeaveSummary::leaveTypeId).orElse(leaveTypeId);
		String type = typeId == null ? "leave" : policyApi.findLeaveType(typeId).map(LeaveTypeInfo::name).orElse("leave");
		return leave.map(found -> type + " (" + (found.startDate().equals(found.endDate()) ? found.startDate()
				: found.startDate() + " to " + found.endDate()) + ", " + plain(found.totalDays()) + " day(s))")
				.orElse(type);
	}

	private static boolean isLeave(ApprovalSubjectType subjectType) {
		return subjectType == ApprovalSubjectType.LEAVE_REQUEST || subjectType == ApprovalSubjectType.LEAVE_CANCELLATION;
	}

	private static String plain(BigDecimal days) {
		return days.stripTrailingZeros().toPlainString();
	}

	private static String truncate(String summary) {
		return summary.length() <= 500 ? summary : summary.substring(0, 497) + "...";
	}

	/** Key/value pairs, skipping null values. */
	private static Map<String, Object> details(Object... keyValues) {
		Map<String, Object> details = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			if (keyValues[i + 1] != null) {
				details.put((String) keyValues[i], keyValues[i + 1]);
			}
		}
		return details;
	}

	/** Who did it; {@link #SYSTEM} for jobs and automatic decisions. */
	private record Actor(UUID userId, String name, UUID employeeId) {

		static final Actor SYSTEM = new Actor(null, "The system", null);

		boolean isEmployee(UUID otherEmployeeId) {
			return employeeId != null && employeeId.equals(otherEmployeeId);
		}

	}

}
