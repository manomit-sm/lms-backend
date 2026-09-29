package com.bsolz.lms.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.bsolz.lms.approval.entity.ApprovalWorkflow;
import com.bsolz.lms.approval.entity.WorkflowRule;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkflowSelectorTest {

	private final UUID sick = UUID.randomUUID();

	private final ApprovalWorkflow standard = workflow("Standard", 1000, true);

	private final ApprovalWorkflow longLeave = workflow("Long", 10, false, new WorkflowRule(null, new BigDecimal("5")));

	private final ApprovalWorkflow sickLeave = workflow("Sick", 5, false, new WorkflowRule(sick, null));

	@Test
	void firstMatchingByPriorityElseTheDefault() {
		List<ApprovalWorkflow> all = List.of(standard, longLeave, sickLeave);

		assertThat(WorkflowSelector.select(all, UUID.randomUUID(), new BigDecimal("2"))).isSameAs(standard);
		assertThat(WorkflowSelector.select(all, UUID.randomUUID(), new BigDecimal("5"))).isSameAs(longLeave);
		// Sick (priority 5) is tried before long leave (10).
		assertThat(WorkflowSelector.select(all, sick, new BigDecimal("8"))).isSameAs(sickLeave);
	}

	@Test
	void ignoresInactiveWorkflows() {
		sickLeave.setActive(false);
		assertThat(WorkflowSelector.select(List.of(standard, sickLeave), sick, BigDecimal.ONE)).isSameAs(standard);
	}

	private static ApprovalWorkflow workflow(String name, int priority, boolean isDefault, WorkflowRule... rules) {
		ApprovalWorkflow workflow = new ApprovalWorkflow();
		workflow.setName(name);
		workflow.setPriority(priority);
		workflow.setDefaultWorkflow(isDefault);
		workflow.getRules().addAll(List.of(rules));
		return workflow;
	}

}
