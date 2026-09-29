package com.bsolz.lms.leavepolicy.web.dto;

import com.bsolz.lms.leavepolicy.api.ResolvedPolicy;
import java.util.UUID;

/**
 * The policy governing an employee's use of a leave type.
 *
 * @param policy null when the employee is not eligible for the leave type
 */
public record EffectivePolicyResponse(UUID leaveTypeId, String leaveTypeCode, String leaveTypeName, boolean eligible,
		ResolvedPolicy policy) {
}
