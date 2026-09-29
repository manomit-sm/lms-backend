package com.bsolz.lms.approval.service;

import com.bsolz.lms.approval.entity.ApprovalWorkflow;
import com.bsolz.lms.approval.entity.WorkflowRule;
import com.bsolz.lms.approval.entity.WorkflowStep;
import com.bsolz.lms.approval.exception.ApprovalErrorCode;
import com.bsolz.lms.approval.mapper.ApprovalMapper;
import com.bsolz.lms.approval.model.enums.ApproverType;
import com.bsolz.lms.approval.repository.ApprovalWorkflowRepository;
import com.bsolz.lms.approval.web.dto.WorkflowRequest;
import com.bsolz.lms.approval.web.dto.WorkflowResponse;
import com.bsolz.lms.approval.web.dto.WorkflowStepDto;
import com.bsolz.lms.identity.api.IdentityApi;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workflow definitions. Changes apply to approvals started afterwards; running approvals keep the steps
 * and approvers they started with. The seeded default workflow can be edited but never loses its role.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class WorkflowService {

	private final ApprovalWorkflowRepository repository;

	private final IdentityApi identityApi;

	private final OrganizationApi organizationApi;

	private final LeavePolicyApi policyApi;

	private final ApprovalMapper mapper;

	@Transactional(readOnly = true)
	public List<WorkflowResponse> list() {
		return repository.findAllByOrderByPriorityAsc().stream().map(mapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public WorkflowResponse get(UUID id) {
		return mapper.toResponse(require(id));
	}

	public WorkflowResponse create(WorkflowRequest request) {
		if (repository.existsByNameIgnoreCase(request.name().trim())) {
			throw nameTaken(request.name());
		}
		if (repository.existsByPriority(request.priority())) {
			throw priorityTaken(request.priority());
		}
		ApprovalWorkflow workflow = new ApprovalWorkflow();
		apply(workflow, request);
		return mapper.toResponse(repository.save(workflow));
	}

	public WorkflowResponse update(UUID id, WorkflowRequest request) {
		ApprovalWorkflow workflow = require(id);
		if (repository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
			throw nameTaken(request.name());
		}
		if (repository.existsByPriorityAndIdNot(request.priority(), id)) {
			throw priorityTaken(request.priority());
		}
		apply(workflow, request);
		repository.flush();
		return mapper.toResponse(workflow);
	}

	private void apply(ApprovalWorkflow workflow, WorkflowRequest request) {
		List<WorkflowRule> rules = request.rules() == null ? List.of()
				: request.rules().stream().map(mapper::toRule).distinct().toList();
		boolean active = request.active() == null || request.active();
		if (workflow.isDefaultWorkflow()) {
			if (!rules.isEmpty() || !active) {
				throw new ApiException(ApprovalErrorCode.DEFAULT_WORKFLOW_FIXED,
						"The default workflow applies when no other does: it has no rules and stays active");
			}
		}
		else if (rules.isEmpty()) {
			throw invalid("Give at least one rule saying when this workflow applies");
		}
		for (WorkflowRule rule : rules) {
			if (rule.leaveTypeId() == null && rule.minDays() == null) {
				throw invalid("Each rule needs a leave type, a minimum number of days, or both");
			}
			if (rule.leaveTypeId() != null && policyApi.findLeaveType(rule.leaveTypeId()).isEmpty()) {
				throw invalid("Unknown leave type " + rule.leaveTypeId());
			}
		}
		request.steps().forEach(this::validate);
		if (request.autoApproveAfterHours() != null && request.escalateAfterHours() != null
				&& request.autoApproveAfterHours() <= request.escalateAfterHours()) {
			throw invalid("Automatic approval must come after escalation");
		}

		workflow.setName(request.name().trim());
		workflow.setDescription(request.description());
		workflow.setPriority(request.priority());
		workflow.setActive(active);
		workflow.setReminderAfterHours(request.reminderAfterHours());
		workflow.setEscalateAfterHours(request.escalateAfterHours());
		workflow.setAutoApproveAfterHours(request.autoApproveAfterHours());
		workflow.getRules().clear();
		workflow.getRules().addAll(rules);
		workflow.getSteps().clear();
		workflow.getSteps().addAll(request.steps().stream().map(mapper::toStep).toList());
	}

	private void validate(WorkflowStepDto step) {
		boolean isRole = step.approverType() == ApproverType.ROLE;
		boolean isEmployee = step.approverType() == ApproverType.EMPLOYEE;
		if (isRole != (step.roleCode() != null) || isEmployee != (step.employeeId() != null)) {
			throw invalid("A ROLE step names a roleCode and an EMPLOYEE step an employeeId; other steps name neither");
		}
		if (isRole && !identityApi.roleExists(step.roleCode())) {
			throw invalid("Unknown role " + step.roleCode());
		}
		if (isEmployee && organizationApi.findEmployee(step.employeeId()).filter(e -> !e.isExited()).isEmpty()) {
			throw invalid("Unknown or exited employee " + step.employeeId());
		}
	}

	private ApprovalWorkflow require(UUID id) {
		return repository.findWithDetailsById(id)
				.orElseThrow(() -> new ApiException(ApprovalErrorCode.WORKFLOW_NOT_FOUND, "Approval workflow not found"));
	}

	private static ApiException invalid(String message) {
		return new ApiException(ApprovalErrorCode.INVALID_WORKFLOW, message);
	}

	private static ApiException nameTaken(String name) {
		return new ApiException(ApprovalErrorCode.WORKFLOW_NAME_TAKEN, "Workflow '" + name + "' already exists");
	}

	private static ApiException priorityTaken(int priority) {
		return new ApiException(ApprovalErrorCode.WORKFLOW_PRIORITY_TAKEN,
				"Another workflow already has priority " + priority);
	}

}
