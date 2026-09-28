package com.bsolz.lms.leavepolicy.web.dto;

import com.bsolz.lms.leavepolicy.model.enums.LeavePeriodStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeavePeriodResponse(UUID id, String name, LocalDate startDate, LocalDate endDate,
		LeavePeriodStatus status, Instant createdAt) {
}
