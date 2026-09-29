package com.bsolz.lms.leave.domain;

import com.bsolz.lms.leave.exception.LeaveErrorCode;

/** A leave rule a request breaks. */
public record Violation(LeaveErrorCode code, String message) {
}
