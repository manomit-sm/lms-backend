package com.bsolz.lms.notification.entity;

import com.bsolz.lms.notification.model.enums.NotificationType;
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

/**
 * A notification for one user. Created only through {@code NotificationRepository#insertIfAbsent}
 * (so a redelivered event can't create it twice); afterwards only its read time changes.
 */
@Getter
@Entity
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

	@Id
	private UUID id;

	private UUID userId;

	@Enumerated(EnumType.STRING)
	private NotificationType type;

	private String title;

	private String message;

	private String subjectType;

	private UUID subjectId;

	private Instant readAt;

	private Instant createdAt;

}
