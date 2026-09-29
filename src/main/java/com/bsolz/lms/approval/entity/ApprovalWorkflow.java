package com.bsolz.lms.approval.entity;

import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ListIndexBase;

/**
 * Which approvers a request needs, in order. Non-default workflows are tried by ascending priority and
 * the first with a matching rule is used; the single default workflow (no rules) catches the rest.
 */
@Getter
@Setter
@Entity
@Table(name = "approval_workflow")
public class ApprovalWorkflow extends BaseEntity {

	private String name;

	private String description;

	private int priority;

	private boolean defaultWorkflow;

	private boolean active = true;

	@ElementCollection
	@CollectionTable(name = "approval_workflow_rule", joinColumns = @JoinColumn(name = "approval_workflow_id"))
	private List<WorkflowRule> rules = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "approval_step", joinColumns = @JoinColumn(name = "approval_workflow_id"))
	@OrderColumn(name = "step_order")
	@ListIndexBase(1)
	private List<WorkflowStep> steps = new ArrayList<>();

	public boolean matches(UUID leaveTypeId, BigDecimal days) {
		return rules.stream().anyMatch(rule -> rule.matches(leaveTypeId, days));
	}

}
