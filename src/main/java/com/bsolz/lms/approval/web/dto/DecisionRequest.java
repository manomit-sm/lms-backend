package com.bsolz.lms.approval.web.dto;

import jakarta.validation.constraints.Size;

/** @param comment optional when approving, required when rejecting */
public record DecisionRequest(@Size(max = 1000) String comment) {
}
