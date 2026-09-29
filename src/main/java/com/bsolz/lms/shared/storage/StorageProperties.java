package com.bsolz.lms.shared.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param provider {@code s3}, or {@code fake} (in memory, local development and tests)
 * @param presignTtl how long presigned URLs stay valid
 */
@ConfigurationProperties("lms.storage")
public record StorageProperties(String provider, Duration presignTtl, S3 s3) {

	public record S3(String bucket, String region) {
	}

}
