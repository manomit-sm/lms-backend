package com.bsolz.lms.shared.storage;

/**
 * Object storage for user files. Clients upload and download directly with presigned URLs, so file
 * bytes never pass through the application. Keys must start with {@code tenants/<tenantId>/}.
 */
public interface FileStorage {

	/** A URL the client PUTs the file to, with exactly the returned headers. */
	PresignedUpload presignUpload(String key, String contentType, long sizeBytes);

	/** A short-lived URL that downloads the file under {@code fileName}. */
	PresignedDownload presignDownload(String key, String fileName);

	boolean exists(String key);

}
