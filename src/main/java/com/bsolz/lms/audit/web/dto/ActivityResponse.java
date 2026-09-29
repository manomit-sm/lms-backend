package com.bsolz.lms.audit.web.dto;

import com.bsolz.lms.audit.model.enums.AuditAction;
import com.bsolz.lms.audit.model.enums.AuditEntityType;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * An audit trail entry.
 *
 * @param actor who did it; null when the system did (a job, or an automatic decision)
 * @param employee the employee it concerns, if any
 * @param summary a readable sentence, with names as they were at the time
 * @param details the event's data; only in the audit log, never in activity feeds
 */
public record ActivityResponse(UUID id, Instant occurredAt, Ref actor, AuditAction action, AuditEntityType entityType,
		UUID entityId, Ref employee, String summary, JsonNode details) {

	public record Ref(UUID id, String name) {
	}

}
