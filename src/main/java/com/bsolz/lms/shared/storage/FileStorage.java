package com.bsolz.lms.shared.storage;

/**
 * Object storage for user files. Clients upload and download directly with presigned URLs, so user
 * uploads never pass through the application; files the application produces itself (report exports)
 * are stored with {@link #put}. Keys must start with {@code tenants/<tenantId>/}.
 */
public interface FileStorage {

	/** A URL the client PUTs the file to, with exactly the returned headers. */
	PresignedUpload presignUpload(String key, String contentType, long sizeBytes);

	/** A short-lived URL that downloads the file under {@code fileName}. */
	PresignedDownload presignDownload(String key, String fileName);

	boolean exists(String key);

	/** Stores a file the application generated. */
	void put(String key, byte[] content, String contentType);

}
