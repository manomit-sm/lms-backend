package com.bsolz.lms.notification.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The open server-sent event streams of this instance, keyed by {@code (tenantId, userId)}: the tenant is
 * part of the key, so a user id can never receive another tenant's events. Instance-local - with several
 * instances a user only gets events published on the instance holding their stream, which is enough for
 * one instance; fanning out across instances (e.g. Postgres LISTEN/NOTIFY) comes later.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(NotificationStreamProperties.class)
public class NotificationStreams {

	private final Map<Key, List<SseEmitter>> streams = new ConcurrentHashMap<>();

	private final NotificationStreamProperties properties;

	/** Opens a stream for the user; beyond {@code maxPerUser} the user's oldest stream is closed. */
	public SseEmitter open(UUID tenantId, UUID userId) {
		Key key = new Key(tenantId, userId);
		SseEmitter emitter = new SseEmitter(properties.timeout().toMillis());
		List<SseEmitter> evicted = new ArrayList<>();
		streams.compute(key, (ignored, current) -> {
			List<SseEmitter> userStreams = current != null ? current : new CopyOnWriteArrayList<>();
			userStreams.add(emitter);
			while (userStreams.size() > properties.maxPerUser()) {
				evicted.add(userStreams.removeFirst());
			}
			return userStreams;
		});
		evicted.forEach(SseEmitter::complete);
		emitter.onCompletion(() -> remove(key, emitter));
		emitter.onTimeout(() -> remove(key, emitter));
		emitter.onError(error -> remove(key, emitter));
		return emitter;
	}

	/** Sends an event to every open stream of the user; a stream that fails is dropped. */
	public void send(UUID tenantId, UUID userId, String eventName, Object data) {
		List<SseEmitter> userStreams = streams.get(new Key(tenantId, userId));
		if (userStreams == null) {
			return;
		}
		for (SseEmitter emitter : userStreams) {
			try {
				emitter.send(SseEmitter.event().name(eventName).data(data));
			}
			catch (IOException | IllegalStateException ex) {
				log.debug("Dropping a notification stream that could not be written to", ex);
				remove(new Key(tenantId, userId), emitter);
				emitter.completeWithError(ex);
			}
		}
	}

	/** Keeps idle streams open through proxies. Instance-local work, so no job lock. */
	@Scheduled(fixedDelayString = "${lms.notification.stream.heartbeat}")
	void heartbeat() {
		streams.forEach((key, userStreams) -> userStreams.forEach(emitter -> {
			try {
				emitter.send(SseEmitter.event().comment("keep-alive"));
			}
			catch (IOException | IllegalStateException ex) {
				remove(key, emitter);
				emitter.completeWithError(ex);
			}
		}));
	}

	private void remove(Key key, SseEmitter emitter) {
		streams.computeIfPresent(key, (ignored, userStreams) -> {
			userStreams.remove(emitter);
			return userStreams.isEmpty() ? null : userStreams;
		});
	}

	private record Key(UUID tenantId, UUID userId) {
	}

}
