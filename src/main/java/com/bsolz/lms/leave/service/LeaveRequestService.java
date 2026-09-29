package com.bsolz.lms.leave.service;

import com.bsolz.lms.approval.api.ApprovalApi;
import com.bsolz.lms.approval.api.StartApproval;
import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import com.bsolz.lms.balance.api.BalanceApi;
import com.bsolz.lms.balance.api.HoldRequest;
import com.bsolz.lms.leave.api.LeaveRequestStatusChanged;
import com.bsolz.lms.leave.domain.LeaveStateMachine;
import com.bsolz.lms.leave.domain.Violation;
import com.bsolz.lms.leave.entity.LeaveAttachment;
import com.bsolz.lms.leave.entity.LeaveRequest;
import com.bsolz.lms.leave.entity.LeaveRequestHistory;
import com.bsolz.lms.leave.exception.LeaveErrorCode;
import com.bsolz.lms.leave.model.enums.LeaveAction;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import com.bsolz.lms.leave.repository.LeaveRequestHistoryRepository;
import com.bsolz.lms.leave.repository.LeaveRequestRepository;
import com.bsolz.lms.leave.web.dto.LeaveDayDto;
import com.bsolz.lms.leave.web.dto.LeaveRequestDetail;
import com.bsolz.lms.leave.web.dto.PreviewResponse;
import com.bsolz.lms.leave.web.dto.ViolationDto;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.settings.api.SettingsApi;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.security.CurrentUser;
import com.bsolz.lms.shared.security.LmsPrincipal;
import com.bsolz.lms.shared.security.Permissions;
import com.bsolz.lms.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leave request commands. Every status change goes through {@link #transition}: checked against
 * {@link LeaveStateMachine}, recorded in the history and published. The balance moves in the same
 * transaction: held on submission, consumed on approval, released on rejection or withdrawal, and given
 * back (reversed) on cancellation.
 * <p>
 * Lock order is always approval, then leave request: deciding an approval locks the approval and then,
 * through {@link ApprovalOutcomeListener}, the request; withdrawing cancels the approval before locking
 * the request.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class LeaveRequestService {

	static final String OVERLAP_CONSTRAINT = "ex_leave_request_overlap";

	private static final Map<LeaveAction, String> ACTION_PHRASES = Map.of(LeaveAction.SUBMIT, "be submitted",
			LeaveAction.APPROVE, "be approved", LeaveAction.REJECT, "be rejected", LeaveAction.WITHDRAW, "be withdrawn",
			LeaveAction.CANCEL, "be cancelled", LeaveAction.REQUEST_CANCELLATION, "be cancelled",
			LeaveAction.APPROVE_CANCELLATION, "have its cancellation approved",
			LeaveAction.REJECT_CANCELLATION, "have its cancellation rejected");

	private final LeaveRequestRepository repository;

	private final LeaveRequestHistoryRepository historyRepository;

	private final LeaveEvaluator evaluator;

	private final LeaveAttachmentService attachmentService;

	private final LeaveViews views;

	private final OrganizationApi organizationApi;

	private final BalanceApi balanceApi;

	private final ApprovalApi approvalApi;

	private final SettingsApi settings;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	/** Works out the request's days and lists every rule it breaks, without saving anything. */
	@Transactional(readOnly = true)
	public PreviewResponse preview(LeaveApplication application) {
		LeaveEvaluator.Evaluation evaluation = evaluator.evaluate(currentEmployee(), application,
				application.attachmentIds().size());
		return new PreviewResponse(evaluation.type().id(),
				evaluation.period() == null ? null : evaluation.period().id(), evaluation.totalDays(),
				evaluation.available(),
				evaluation.available() == null ? null : evaluation.available().subtract(evaluation.totalDays()),
				evaluation.violations().isEmpty(),
				evaluation.violations().stream().map(v -> new ViolationDto(v.code().code(), v.message())).toList(),
				evaluation.days().stream().map(day -> new LeaveDayDto(day.date(), day.dayType(), day.session(),
						day.amount())).toList());
	}

	/**
	 * Submits the current user's request: holds its days and starts approval. If no approval step has an
	 * approver, it is approved before this returns.
	 */
	public LeaveRequestDetail submit(LeaveApplication application) {
		LmsPrincipal user = CurrentUser.require();
		EmployeeSummary employee = currentEmployee();
		List<LeaveAttachment> attachments = attachmentService.requireUnused(application.attachmentIds(), user.userId());
		LeaveEvaluator.Evaluation evaluation = evaluator.evaluate(employee, application, attachments.size());
		if (!evaluation.violations().isEmpty()) {
			Violation first = evaluation.violations().getFirst();
			throw new ApiException(first.code(), first.message(), Map.of("violations", evaluation.violations().stream()
					.map(v -> Map.of("code", v.code().code(), "message", v.message())).toList()));
		}

		LeaveRequest request = new LeaveRequest(employee.id(), evaluation.type().id(), evaluation.period().id(),
				application.startDate(), application.endDate(), application.startSession(), application.endSession(),
				evaluation.days(), evaluation.totalDays(), application.reason());
		try {
			repository.saveAndFlush(request);
		}
		catch (DataIntegrityViolationException ex) {
			if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(OVERLAP_CONSTRAINT)) {
				throw new ApiException(LeaveErrorCode.OVERLAPPING_LEAVE,
						"The dates overlap another pending or approved leave request");
			}
			throw ex;
		}
		attachments.forEach(attachment -> attachment.linkTo(request.getId()));
		record(request, null, LeaveAction.SUBMIT, user.userId(), null);

		balanceApi.hold(new HoldRequest(employee.id(), request.getLeaveTypeId(), request.getStartDate(),
				request.getTotalDays(), request.getId()));
		approvalApi.start(new StartApproval(ApprovalSubjectType.LEAVE_REQUEST, request.getId(), employee.id(),
				user.userId(), request.getLeaveTypeId(), request.getTotalDays()));
		return views.detail(request);
	}

	/** The requester takes back a request that is still pending. */
	public LeaveRequestDetail withdraw(UUID id) {
		LmsPrincipal user = CurrentUser.require();
		approvalApi.cancel(ApprovalSubjectType.LEAVE_REQUEST, id);
		LeaveRequest request = lock(id);
		if (!request.getEmployeeId().equals(user.employeeId())) {
			throw new ApiException(LeaveErrorCode.NOT_REQUESTER, "Only the requester can withdraw a request");
		}
		transition(request, LeaveAction.WITHDRAW, user.userId(), null);
		balanceApi.release(id);
		return views.detail(request);
	}

	/**
	 * Cancels approved leave. Before it starts - or when done by someone with LEAVE_MANAGE - it is cancelled
	 * at once and its days go back to the balance. Once it has started, cancelling needs approval: the
	 * request waits in CANCELLATION_PENDING and its days stay used until the cancellation is approved.
	 */
	public LeaveRequestDetail cancel(UUID id, String reason) {
		LmsPrincipal user = CurrentUser.require();
		LeaveRequest request = lock(id);
		boolean leaveManager = user.permissions().contains(Permissions.LEAVE_MANAGE);
		boolean requester = request.getEmployeeId().equals(user.employeeId());
		if (!requester && !leaveManager) {
			throw new ApiException(LeaveErrorCode.NOT_REQUESTER, "Only the requester or HR can cancel leave");
		}
		request.setCancellationReason(reason.trim());
		if (leaveManager || request.getStartDate().isAfter(settings.today())) {
			transition(request, LeaveAction.CANCEL, user.userId(), reason.trim());
			balanceApi.reverse(id);
		}
		else {
			transition(request, LeaveAction.REQUEST_CANCELLATION, user.userId(), reason.trim());
			approvalApi.start(new StartApproval(ApprovalSubjectType.LEAVE_CANCELLATION, id, request.getEmployeeId(),
					user.userId(), request.getLeaveTypeId(), request.getTotalDays()));
		}
		return views.detail(request);
	}

	/**
	 * Approval completed. {@code approverUserId} is null when it completed automatically, and
	 * {@code automaticNote} then says why.
	 */
	void approved(UUID id, UUID approverUserId, String automaticNote) {
		LeaveRequest request = lock(id);
		transition(request, LeaveAction.APPROVE, approverUserId, automaticNote);
		balanceApi.consume(id);
	}

	void rejected(UUID id, UUID approverUserId, String comment) {
		LeaveRequest request = lock(id);
		transition(request, LeaveAction.REJECT, approverUserId, comment);
		balanceApi.release(id);
	}

	void cancellationApproved(UUID id, UUID approverUserId, String automaticNote) {
		LeaveRequest request = lock(id);
		transition(request, LeaveAction.APPROVE_CANCELLATION, approverUserId, automaticNote);
		balanceApi.reverse(id);
	}

	void cancellationRejected(UUID id, UUID approverUserId, String comment) {
		LeaveRequest request = lock(id);
		transition(request, LeaveAction.REJECT_CANCELLATION, approverUserId, comment);
	}

	private void transition(LeaveRequest request, LeaveAction action, UUID actorUserId, String comment) {
		LeaveStatus from = request.getStatus();
		LeaveStatus to = LeaveStateMachine.next(from, action).orElseThrow(() -> new ApiException(
				LeaveErrorCode.INVALID_STATUS_TRANSITION, "A request that is " + from.name().toLowerCase().replace('_', ' ')
						+ " can't " + ACTION_PHRASES.get(action)));
		request.moveTo(to, Instant.now(clock));
		record(request, from, action, actorUserId, comment);
	}

	private void record(LeaveRequest request, LeaveStatus from, LeaveAction action, UUID actorUserId, String comment) {
		Instant now = Instant.now(clock);
		historyRepository.save(new LeaveRequestHistory(request.getId(), from, request.getStatus(), action, actorUserId,
				comment, now));
		events.publishEvent(new LeaveRequestStatusChanged(TenantContext.require().id(), request.getId(),
				request.getEmployeeId(), request.getLeaveTypeId(), from, request.getStatus(), action, actorUserId,
				comment, now));
	}

	private LeaveRequest lock(UUID id) {
		return repository.findByIdForUpdate(id)
				.orElseThrow(() -> new ApiException(LeaveErrorCode.LEAVE_REQUEST_NOT_FOUND, "Leave request not found"));
	}

	private EmployeeSummary currentEmployee() {
		UUID employeeId = CurrentUser.require().employeeId();
		return (employeeId == null ? Optional.<EmployeeSummary>empty() : organizationApi.findEmployee(employeeId))
				.orElseThrow(() -> new ApiException(LeaveErrorCode.NOT_LINKED_TO_EMPLOYEE,
						"Your user is not linked to an employee record"));
	}

}
