package com.bsolz.lms.notification.web;

import com.bsolz.lms.notification.service.NotificationService;
import com.bsolz.lms.notification.web.dto.MarkedReadResponse;
import com.bsolz.lms.notification.web.dto.NotificationResponse;
import com.bsolz.lms.notification.web.dto.UnreadCountResponse;
import com.bsolz.lms.shared.web.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** The current user's own notifications; nobody sees anyone else's. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
class NotificationController {

	private final NotificationService service;

	/** Newest first. */
	@GetMapping
	PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
			@PageableDefault(size = 20) Pageable pageable) {
		return service.list(unreadOnly, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
	}

	@GetMapping("/unread-count")
	UnreadCountResponse unreadCount() {
		return service.unreadCount();
	}

	@PostMapping("/{id}/read")
	ResponseEntity<Void> markRead(@PathVariable UUID id) {
		service.markRead(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/read-all")
	MarkedReadResponse markAllRead() {
		return service.markAllRead();
	}

	/**
	 * Server-sent events: {@code unread-count} ({@code {"count": n}}) when the stream opens, then a
	 * {@code notification} event (a notification as in the list) for each new one, plus comment
	 * heartbeats. The stream closes after a while; reconnect when it does. Authenticate with the usual
	 * bearer header (e.g. with a fetch-based EventSource client) - tokens are never accepted in the URL.
	 */
	@GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter stream() {
		return service.openStream();
	}

}
