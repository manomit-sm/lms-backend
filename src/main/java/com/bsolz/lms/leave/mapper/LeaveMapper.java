package com.bsolz.lms.leave.mapper;

import com.bsolz.lms.leave.entity.LeaveAttachment;
import com.bsolz.lms.leave.entity.LeaveDay;
import com.bsolz.lms.leave.entity.LeaveRequestHistory;
import com.bsolz.lms.leave.web.dto.AttachmentResponse;
import com.bsolz.lms.leave.web.dto.HistoryEntry;
import com.bsolz.lms.leave.web.dto.LeaveDayDto;
import com.bsolz.lms.leavepolicy.api.LeaveTypeInfo;
import com.bsolz.lms.leave.web.dto.LeaveTypeRef;
import org.mapstruct.Mapper;

@Mapper
public interface LeaveMapper {

	LeaveDayDto toDto(LeaveDay day);

	AttachmentResponse toResponse(LeaveAttachment attachment);

	HistoryEntry toEntry(LeaveRequestHistory history);

	LeaveTypeRef toRef(LeaveTypeInfo leaveType);

}
