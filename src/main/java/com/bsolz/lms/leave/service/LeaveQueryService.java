package com.bsolz.lms.leave.service;

import com.bsolz.lms.approval.api.ApprovalApi;
import com.bsolz.lms.approval.api.PendingTask;
import com.bsolz.lms.leave.entity.LeaveRequest;
import com.bsolz.lms.leave.exception.LeaveErrorCode;
import com.bsolz.lms.leave.repository.LeaveRequestRepository;
import com.bsolz.lms.leave.web.dto.AttachmentDownloadResponse;
import com.bsolz.lms.leave.web.dto.LeaveRequestDetail;
import com.bsolz.lms.leave.web.dto.LeaveRequestSummary;
import com.bsolz.lms.leave.web.dto.PendingApprovalResponse;
import com.bsolz.lms.organization.api.EmployeeVisibility;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.security.CurrentUser;
import com.bsolz.lms.shared.security.DataScope;
import com.bsolz.lms.shared.security.LmsPrincipal;
import com.bsolz.lms.shared.security.Permissions;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading leave requests. A user sees a request if it is theirs, if its employee is in their data
 * scope (reporting line / everyone), if they hold LEAVE_MANAGE, or if they are one of its approvers.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaveQueryService {

	private final LeaveRequestRepository repository;

	private final LeaveViews views;

	private final LeaveAttachmentService attachmentService;

	private final ApprovalApi approvalApi;

	private final EmployeeVisibility employeeVisibility;

	private final OrganizationApi organizationApi;

	public LeaveRequestDetail get(UUID id) {
		return views.detail(requireVisible(id));
	}

	public AttachmentDownloadResponse download(UUID id, UUID attachmentId) {
		requireVisible(id);
		return attachmentService.download(id, attachmentId);
	}

	/** Requests of one employee, or of everyone the caller can see; newest first by default. */
	public Page<LeaveRequestSummary> list(LeaveFilter filter, Pageable pageable) {
		LmsPrincipal user = CurrentUser.require();
		boolean everyone = user.permissions().contains(Permissions.LEAVE_MANAGE)
				|| employeeVisibility.currentScope() == DataScope.TENANT;
		Set<UUID> employees;
		if (filter.employeeId() != null) {
			if (!everyone && !employeeVisibility.canView(filter.employeeId())) {
				throw new AccessDeniedException("Employee not visible");
			}
			employees = Set.of(filter.employeeId());
		}
		else if (everyone) {
			employees = null;
		}
		else {
			employees = new HashSet<>();
			if (user.employeeId() != null) {
				employees.add(user.employeeId());
				if (employeeVisibility.currentScope() == DataScope.TEAM) {
					employees.addAll(organizationApi.findReportingLine(user.employeeId()));
				}
			}
		}
		Specification<LeaveRequest> specification = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (employees != null) {
				predicates.add(employees.isEmpty() ? cb.disjunction() : root.get("employeeId").in(employees));
			}
			if (filter.status() != null) {
				predicates.add(cb.equal(root.get("status"), filter.status()));
			}
			if (filter.leaveTypeId() != null) {
				predicates.add(cb.equal(root.get("leaveTypeId"), filter.leaveTypeId()));
			}
			if (filter.from() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), filter.from()));
			}
			if (filter.to() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), filter.to()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
		Page<LeaveRequest> page = repository.findAll(specification, pageable);
		return new PageImpl<>(views.summaries(page.getContent()), pageable, page.getTotalElements());
	}

	/** Leave requests (and cancellations) waiting for the current user's decision, oldest first. */
	public List<PendingApprovalResponse> pendingApproval() {
		List<PendingTask> tasks = approvalApi.findPendingTasks(CurrentUser.require().userId());
		Map<UUID, LeaveRequestSummary> requests = views.summaries(
				repository.findAllByIdIn(tasks.stream().map(PendingTask::subjectId).collect(Collectors.toSet())))
				.stream().collect(Collectors.toMap(LeaveRequestSummary::id, Function.identity()));
		return tasks.stream().filter(task -> requests.containsKey(task.subjectId()))
				.map(task -> new PendingApprovalResponse(task.taskId(), task.subjectType(), task.stepOrder(),
						task.approverDescription(), task.assignedAt(), requests.get(task.subjectId())))
				.toList();
	}

	private LeaveRequest requireVisible(UUID id) {
		LeaveRequest request = repository.findById(id)
				.orElseThrow(() -> new ApiException(LeaveErrorCode.LEAVE_REQUEST_NOT_FOUND, "Leave request not found"));
		LmsPrincipal user = CurrentUser.require();
		boolean visible = request.getEmployeeId().equals(user.employeeId())
				|| user.permissions().contains(Permissions.LEAVE_MANAGE)
				|| employeeVisibility.canView(request.getEmployeeId())
				|| approvalApi.isApprover(user.userId(), id);
		if (!visible) {
			throw new AccessDeniedException("Leave request not visible");
		}
		return request;
	}

}
