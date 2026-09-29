package com.bsolz.lms.leavepolicy.mapper;

import com.bsolz.lms.leavepolicy.api.LeavePeriodInfo;
import com.bsolz.lms.leavepolicy.api.LeaveTypeInfo;
import com.bsolz.lms.leavepolicy.api.ResolvedPolicy;
import com.bsolz.lms.leavepolicy.entity.ApplicabilityRule;
import com.bsolz.lms.leavepolicy.entity.LeavePeriod;
import com.bsolz.lms.leavepolicy.entity.LeavePolicy;
import com.bsolz.lms.leavepolicy.entity.LeaveType;
import com.bsolz.lms.leavepolicy.web.dto.LeavePeriodResponse;
import com.bsolz.lms.leavepolicy.web.dto.LeavePolicyResponse;
import com.bsolz.lms.leavepolicy.web.dto.LeaveTypeResponse;
import com.bsolz.lms.leavepolicy.web.dto.PolicyRule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Entity to response/API mapping. Call inside a transaction: a policy's leave type is lazy. */
@Mapper
public interface LeavePolicyMapper {

	LeaveTypeResponse toResponse(LeaveType leaveType);

	LeaveTypeInfo toInfo(LeaveType leaveType);

	LeavePeriodResponse toResponse(LeavePeriod period);

	LeavePeriodInfo toInfo(LeavePeriod period);

	LeavePolicyResponse toResponse(LeavePolicy policy);

	LeavePolicyResponse.LeaveTypeRef toRef(LeaveType leaveType);

	PolicyRule toDto(ApplicabilityRule rule);

	ApplicabilityRule toRule(PolicyRule rule);

	@Mapping(target = "policyId", source = "id")
	@Mapping(target = "policyName", source = "name")
	@Mapping(target = "leaveTypeId", source = "leaveType.id")
	ResolvedPolicy toResolved(LeavePolicy policy);

}
