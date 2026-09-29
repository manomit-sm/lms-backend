package com.bsolz.lms.shared.storage;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local development and tests: nothing is kept on disk. A presigned upload counts as uploaded, so flows
 * that check {@link #exists} work without a real client upload; files stored with {@link #put} are kept
 * in memory and can be read back with {@link #content}.
 */
public class InMemoryFileStorage implements FileStorage {

	private static final String BASE = "https://storage.invalid/";

	private final Set<String> keys = ConcurrentHashMap.newKeySet();

	private final Map<String, byte[]> contents = new ConcurrentHashMap<>();

	private final Duration ttl;

	private final Clock clock;

	public InMemoryFileStorage(Duration ttl, Clock clock) {
		this.ttl = ttl;
		this.clock = clock;
	}

	@Override
	public PresignedUpload presignUpload(String key, String contentType, long sizeBytes) {
		keys.add(key);
		return new PresignedUpload(URI.create(BASE + key), "PUT", Map.of("Content-Type", contentType), expiry());
	}

	@Override
	public PresignedDownload presignDownload(String key, String fileName) {
		return new PresignedDownload(URI.create(BASE + key), expiry());
	}

	@Override
	public boolean exists(String key) {
		return keys.contains(key);
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		keys.add(key);
		contents.put(key, content.clone());
	}

	/** Tests: what {@link #put} stored under the key. */
	public Optional<byte[]> content(String key) {
		return Optional.ofNullable(contents.get(key)).map(byte[]::clone);
	}

	/** Tests: pretend an upload never happened. */
	public void forget(String key) {
		keys.remove(key);
		contents.remove(key);
	}

	private Instant expiry() {
		return Instant.now(clock).plus(ttl);
	}

}
