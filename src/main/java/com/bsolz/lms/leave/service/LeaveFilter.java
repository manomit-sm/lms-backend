package com.bsolz.lms.leave.service;

import com.bsolz.lms.leave.model.enums.LeaveStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param employeeId one employee; null for everyone the caller can see
 * @param from with {@code to}: requests overlapping this range
 */
public record LeaveFilter(UUID employeeId, LeaveStatus status, UUID leaveTypeId, LocalDate from, LocalDate to) {
}
