package com.bsolz.lms.notification.web.dto;

import com.bsolz.lms.notification.model.enums.NotificationType;
import java.time.Instant;
import java.util.UUID;

/**
 * @param subjectType what it is about, e.g. {@code LEAVE_REQUEST}; with {@code subjectId} the frontend can
 * link to it
 * @param readAt null while unread
 */
public record NotificationResponse(UUID id, NotificationType type, String title, String message, String subjectType,
		UUID subjectId, Instant createdAt, Instant readAt) {
}
