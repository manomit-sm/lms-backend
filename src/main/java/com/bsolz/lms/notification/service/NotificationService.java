package com.bsolz.lms.notification.service;

import com.bsolz.lms.notification.entity.Notification;
import com.bsolz.lms.notification.exception.NotificationErrorCode;
import com.bsolz.lms.notification.model.enums.NotificationType;
import com.bsolz.lms.notification.repository.NotificationRepository;
import com.bsolz.lms.notification.web.dto.MarkedReadResponse;
import com.bsolz.lms.notification.web.dto.NotificationResponse;
import com.bsolz.lms.notification.web.dto.UnreadCountResponse;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.security.CurrentUser;
import com.bsolz.lms.shared.security.LmsPrincipal;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.shared.web.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Creates notifications and serves the current user's. New notifications are pushed to the recipients'
 * open streams once the creating transaction has committed, as a {@value #NOTIFICATION_EVENT} event.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

	static final String NOTIFICATION_EVENT = "notification";

	static final String UNREAD_COUNT_EVENT = "unread-count";

	private final NotificationRepository repository;

	private final NotificationStreams streams;

	private final Clock clock;

	/**
	 * Notifies each recipient once per {@code dedupKey}: a recipient who already has a notification with
	 * that key (e.g. from an earlier delivery of the same event) gets nothing new.
	 */
	public void notify(Collection<UUID> recipientUserIds, NotificationType type, String title, String message,
			String subjectType, UUID subjectId, String dedupKey) {
		UUID tenantId = TenantContext.require().id();
		Instant now = Instant.now(clock);
		List<Runnable> pushes = new ArrayList<>();
		for (UUID userId : Set.copyOf(recipientUserIds)) {
			UUID id = UUID.randomUUID();
			if (repository.insertIfAbsent(id, userId, type.name(), title, message, subjectType, subjectId, dedupKey,
					now) == 1) {
				NotificationResponse created = new NotificationResponse(id, type, title, message, subjectType, subjectId,
						now, null);
				pushes.add(() -> streams.send(tenantId, userId, NOTIFICATION_EVENT, created));
			}
		}
		if (!pushes.isEmpty()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					pushes.forEach(Runnable::run);
				}
			});
		}
	}

	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> list(boolean unreadOnly, Pageable pageable) {
		UUID userId = currentUserId();
		return PageResponse.from((unreadOnly ? repository.findUnreadForUser(userId, pageable)
				: repository.findForUser(userId, pageable)).map(NotificationService::toResponse));
	}

	@Transactional(readOnly = true)
	public UnreadCountResponse unreadCount() {
		return new UnreadCountResponse(repository.countByUserIdAndReadAtIsNull(currentUserId()));
	}

	public void markRead(UUID id) {
		UUID userId = currentUserId();
		if (!repository.existsByIdAndUserId(id, userId)) {
			throw new ApiException(NotificationErrorCode.NOTIFICATION_NOT_FOUND, "Notification not found");
		}
		repository.markRead(userId, List.of(id), Instant.now(clock));
	}

	public MarkedReadResponse markAllRead() {
		return new MarkedReadResponse(repository.markAllRead(currentUserId(), Instant.now(clock)));
	}

	/**
	 * Opens a stream for the current user. The first event is {@value #UNREAD_COUNT_EVENT} with the unread
	 * count, so a reconnecting client catches up on what it missed.
	 */
	@Transactional(readOnly = true)
	public SseEmitter openStream() {
		LmsPrincipal user = CurrentUser.require();
		UUID userId = currentUserId();
		SseEmitter emitter = streams.open(user.tenant().id(), userId);
		streams.send(user.tenant().id(), userId, UNREAD_COUNT_EVENT,
				new UnreadCountResponse(repository.countByUserIdAndReadAtIsNull(userId)));
		return emitter;
	}

	private static UUID currentUserId() {
		UUID userId = CurrentUser.require().userId();
		if (userId == null) {
			throw new ApiException(NotificationErrorCode.NOT_A_USER, "Notifications need a tenant user");
		}
		return userId;
	}

	private static NotificationResponse toResponse(Notification notification) {
		return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
				notification.getMessage(), notification.getSubjectType(), notification.getSubjectId(),
				notification.getCreatedAt(), notification.getReadAt());
	}

}
