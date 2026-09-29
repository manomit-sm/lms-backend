package com.bsolz.lms.leavepolicy.web.dto;

import java.time.Instant;
import java.util.UUID;

public record LeaveTypeResponse(UUID id, String code, String name, String description, String color, boolean paid,
		boolean balanceTracked, boolean timeOff, boolean halfDayAllowed, boolean active, int sortOrder, Instant createdAt,
		Instant updatedAt) {
}
