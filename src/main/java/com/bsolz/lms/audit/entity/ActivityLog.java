package com.bsolz.lms.audit.entity;

import com.bsolz.lms.audit.model.enums.AuditAction;
import com.bsolz.lms.audit.model.enums.AuditEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * An audit trail entry. Written only by {@code ActivityLogRepository#insertIfAbsent}; append-only (a
 * database trigger rejects updates and deletes).
 */
@Getter
@Entity
@Immutable
@Table(name = "activity_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityLog {

	@Id
	private UUID id;

	private Long seq;

	private Instant occurredAt;

	private UUID actorUserId;

	private String actorName;

	@Enumerated(EnumType.STRING)
	private AuditAction action;

	@Enumerated(EnumType.STRING)
	private AuditEntityType entityType;

	private UUID entityId;

	private UUID employeeId;

	private String employeeName;

	private String summary;

	/** JSON. */
	@Column(columnDefinition = "jsonb")
	private String details;

}
