package com.bsolz.lms.approval.domain;

import com.bsolz.lms.approval.entity.ApprovalWorkflow;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Picks the workflow for a request: the first active non-default one (by priority) with a matching rule, else the default. */
public final class WorkflowSelector {

	private WorkflowSelector() {
	}

	public static ApprovalWorkflow select(List<ApprovalWorkflow> workflows, UUID leaveTypeId, BigDecimal days) {
		return workflows.stream()
				.filter(workflow -> workflow.isActive() && !workflow.isDefaultWorkflow())
				.sorted(Comparator.comparingInt(ApprovalWorkflow::getPriority))
				.filter(workflow -> workflow.matches(leaveTypeId, days))
				.findFirst()
				.or(() -> workflows.stream().filter(ApprovalWorkflow::isDefaultWorkflow).findFirst())
				.orElseThrow(() -> new IllegalStateException("Tenant has no default approval workflow"));
	}

}
