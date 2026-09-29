package com.bsolz.lms.balance.mapper;

import com.bsolz.lms.balance.api.BalanceSnapshot;
import com.bsolz.lms.balance.entity.LeaveBalance;
import com.bsolz.lms.balance.entity.LeaveBalanceTransaction;
import com.bsolz.lms.balance.web.dto.BalanceResponse;
import com.bsolz.lms.balance.web.dto.BalanceTransactionResponse;
import com.bsolz.lms.leavepolicy.api.LeavePeriodInfo;
import com.bsolz.lms.leavepolicy.api.LeaveTypeInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface BalanceMapper {

	@Mapping(target = "balanceId", source = "id")
	BalanceSnapshot toSnapshot(LeaveBalance balance);

	BalanceTransactionResponse toResponse(LeaveBalanceTransaction transaction);

	@Mapping(target = "id", source = "balance.id")
	@Mapping(target = "employeeId", source = "balance.employeeId")
	@Mapping(target = "updatedAt", source = "balance.updatedAt")
	@Mapping(target = "leaveType", source = "leaveType")
	@Mapping(target = "leavePeriod", source = "leavePeriod")
	BalanceResponse toResponse(LeaveBalance balance, LeaveTypeInfo leaveType, LeavePeriodInfo leavePeriod);

	BalanceResponse.LeaveTypeRef toRef(LeaveTypeInfo leaveType);

	BalanceResponse.LeavePeriodRef toRef(LeavePeriodInfo leavePeriod);

}
