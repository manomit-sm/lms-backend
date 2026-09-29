package com.bsolz.lms.reporting.web.dto;

import java.math.BigDecimal;

public record LeaveTypeDays(LeaveTypeRef leaveType, BigDecimal days) {
}
