package com.bsolz.lms.shared.storage;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.util.UriUtils;

/**
 * Local development and tests: nothing is kept on disk. A presigned upload counts as uploaded, so flows
 * that check {@link #exists} work without a real client upload. Presigned URLs point at
 * {@code baseUrl} - in local development {@link LocalStorageController}, so a browser can really upload
 * and download - and files uploaded there or stored with {@link #put} are kept in memory.
 */
public class InMemoryFileStorage implements FileStorage {

	private final String baseUrl;

	/** Presigned uploads by key: the content type and size the upload was presigned for. */
	private final Map<String, Expected> presigned = new ConcurrentHashMap<>();

	private final Map<String, StoredFile> files = new ConcurrentHashMap<>();

	private final Duration ttl;

	private final Clock clock;

	public InMemoryFileStorage(String baseUrl, Duration ttl, Clock clock) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
		this.ttl = ttl;
		this.clock = clock;
	}

	@Override
	public PresignedUpload presignUpload(String key, String contentType, long sizeBytes) {
		presigned.put(key, new Expected(contentType, sizeBytes));
		return new PresignedUpload(URI.create(baseUrl + key), "PUT", Map.of("Content-Type", contentType), expiry());
	}

	@Override
	public PresignedDownload presignDownload(String key, String fileName) {
		return new PresignedDownload(
				URI.create(baseUrl + key + "?fileName=" + UriUtils.encodeQueryParam(fileName, StandardCharsets.UTF_8)),
				expiry());
	}

	@Override
	public boolean exists(String key) {
		return presigned.containsKey(key) || files.containsKey(key);
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		files.put(key, new StoredFile(content.clone(), contentType));
	}

	/**
	 * Local development: what a client PUTs to a presigned URL. Like S3, it accepts only a presigned key,
	 * and at most the presigned size; the file keeps the presigned content type.
	 */
	public boolean upload(String key, byte[] content) {
		Expected expected = presigned.get(key);
		if (expected == null || content.length > expected.sizeBytes()) {
			return false;
		}
		files.put(key, new StoredFile(content.clone(), expected.contentType()));
		return true;
	}

	/** The file uploaded or stored under the key, if any. */
	public Optional<StoredFile> file(String key) {
		return Optional.ofNullable(files.get(key));
	}

	/** Tests: what was stored under the key. */
	public Optional<byte[]> content(String key) {
		return file(key).map(StoredFile::content);
	}

	/** Tests: pretend an upload never happened. */
	public void forget(String key) {
		presigned.remove(key);
		files.remove(key);
	}

	private Instant expiry() {
		return Instant.now(clock).plus(ttl);
	}

	private record Expected(String contentType, long sizeBytes) {
	}

	public record StoredFile(byte[] content, String contentType) {

		@Override
		public byte[] content() {
			return content.clone();
		}

	}

}
