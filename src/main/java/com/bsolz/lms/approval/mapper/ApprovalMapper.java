package com.bsolz.lms.approval.mapper;

import com.bsolz.lms.approval.entity.ApprovalWorkflow;
import com.bsolz.lms.approval.entity.WorkflowRule;
import com.bsolz.lms.approval.entity.WorkflowStep;
import com.bsolz.lms.approval.web.dto.WorkflowResponse;
import com.bsolz.lms.approval.web.dto.WorkflowRuleDto;
import com.bsolz.lms.approval.web.dto.WorkflowStepDto;
import org.mapstruct.Mapper;

@Mapper
public interface ApprovalMapper {

	WorkflowResponse toResponse(ApprovalWorkflow workflow);

	WorkflowRuleDto toDto(WorkflowRule rule);

	WorkflowStepDto toDto(WorkflowStep step);

	WorkflowRule toRule(WorkflowRuleDto rule);

	WorkflowStep toStep(WorkflowStepDto step);

}
