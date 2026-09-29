package com.bsolz.lms.approval.api;

import com.bsolz.lms.approval.model.enums.ApprovalTaskStatus;
import com.bsolz.lms.approval.model.enums.ApproverType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One step of an approval, for display. */
public record ApprovalStepView(UUID taskId, int stepOrder, ApproverType approverType, String approverDescription,
		ApprovalTaskStatus status, List<ApproverRef> assignees, ApproverRef actedBy, Instant actedAt, String comment) {
}
