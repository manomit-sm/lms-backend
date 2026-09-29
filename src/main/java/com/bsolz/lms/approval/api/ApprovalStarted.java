package com.bsolz.lms.approval.api;

import com.bsolz.lms.approval.model.enums.ApprovalStatus;
import java.util.UUID;

/** @param status PENDING, or APPROVED when no step had an approver */
public record ApprovalStarted(UUID approvalId, ApprovalStatus status) {
}
