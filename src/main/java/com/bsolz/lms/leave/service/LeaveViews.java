package com.bsolz.lms.leave.service;

import com.bsolz.lms.approval.api.ApprovalApi;
import com.bsolz.lms.leave.entity.LeaveRequest;
import com.bsolz.lms.leave.mapper.LeaveMapper;
import com.bsolz.lms.leave.repository.LeaveAttachmentRepository;
import com.bsolz.lms.leave.repository.LeaveRequestHistoryRepository;
import com.bsolz.lms.leave.web.dto.EmployeeRef;
import com.bsolz.lms.leave.web.dto.LeaveRequestDetail;
import com.bsolz.lms.leave.web.dto.LeaveRequestSummary;
import com.bsolz.lms.leave.web.dto.LeaveTypeRef;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.organization.api.OrganizationApi;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds leave responses, looking up employees and leave types in bulk. */
@Component
@RequiredArgsConstructor
class LeaveViews {

	private final OrganizationApi organizationApi;

	private final LeavePolicyApi policyApi;

	private final ApprovalApi approvalApi;

	private final LeaveAttachmentRepository attachmentRepository;

	private final LeaveRequestHistoryRepository historyRepository;

	private final LeaveMapper mapper;

	List<LeaveRequestSummary> summaries(Collection<LeaveRequest> requests) {
		Map<UUID, EmployeeRef> employees = new HashMap<>();
		organizationApi.findEmployees(requests.stream().map(LeaveRequest::getEmployeeId).distinct().toList())
				.forEach(employee -> employees.put(employee.id(), new EmployeeRef(employee.id(), employee.fullName())));
		Map<UUID, LeaveTypeRef> types = new HashMap<>();
		requests.stream().map(LeaveRequest::getLeaveTypeId).distinct()
				.forEach(id -> types.put(id, policyApi.findLeaveType(id).map(mapper::toRef).orElse(null)));
		return requests.stream().map(request -> summary(request, employees.get(request.getEmployeeId()),
				types.get(request.getLeaveTypeId()))).toList();
	}

	LeaveRequestSummary summary(LeaveRequest request) {
		return summaries(List.of(request)).getFirst();
	}

	LeaveRequestDetail detail(LeaveRequest request) {
		EmployeeRef employee = organizationApi.findEmployee(request.getEmployeeId())
				.map(found -> new EmployeeRef(found.id(), found.fullName())).orElse(null);
		LeaveTypeRef type = policyApi.findLeaveType(request.getLeaveTypeId()).map(mapper::toRef).orElse(null);
		return new LeaveRequestDetail(request.getId(), employee, type, request.getLeavePeriodId(),
				request.getStartDate(), request.getEndDate(), request.getStartSession(), request.getEndSession(),
				request.getTotalDays(), request.getReason(), request.getStatus(), request.getCancellationReason(),
				request.getCreatedAt(), request.getDecidedAt(), request.getDays().stream().map(mapper::toDto).toList(),
				attachmentRepository.findAllByLeaveRequestIdOrderByCreatedAtAsc(request.getId()).stream()
						.map(mapper::toResponse).toList(),
				approvalApi.findApprovals(request.getId()),
				historyRepository.findAllByLeaveRequestIdOrderByCreatedAtAsc(request.getId()).stream()
						.map(mapper::toEntry).toList());
	}

	private static LeaveRequestSummary summary(LeaveRequest request, EmployeeRef employee, LeaveTypeRef type) {
		return new LeaveRequestSummary(request.getId(), employee, type, request.getStartDate(), request.getEndDate(),
				request.getStartSession(), request.getEndSession(), request.getTotalDays(), request.getStatus(),
				request.getCreatedAt(), request.getDecidedAt());
	}

}
