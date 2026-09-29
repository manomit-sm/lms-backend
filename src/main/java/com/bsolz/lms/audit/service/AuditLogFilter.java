package com.bsolz.lms.audit.service;

import com.bsolz.lms.audit.model.enums.AuditAction;
import com.bsolz.lms.audit.model.enums.AuditEntityType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Audit log search; every criterion is optional.
 *
 * @param from first day (inclusive), in the tenant's timezone
 * @param to last day (inclusive), in the tenant's timezone
 */
public record AuditLogFilter(LocalDate from, LocalDate to, UUID actorUserId, UUID employeeId, AuditAction action,
		AuditEntityType entityType, UUID entityId) {
}
