package com.bsolz.lms.leavepolicy.api;

import java.util.UUID;

/**
 * A leave type as seen by other modules.
 *
 * @param balanceTracked whether requests draw on a balance; untracked types (e.g. unpaid leave, work
 * from home) are never limited by one
 */
public record LeaveTypeInfo(UUID id, String code, String name, String color, boolean paid, boolean balanceTracked,
		boolean halfDayAllowed, boolean active) {
}
