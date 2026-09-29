package com.bsolz.lms.shared.storage;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** S3-backed storage. The signed PUT fixes content type and length, so a client can't upload something else. */
public class S3FileStorage implements FileStorage {

	private final S3Client s3;

	private final S3Presigner presigner;

	private final String bucket;

	private final Duration ttl;

	public S3FileStorage(S3Client s3, S3Presigner presigner, String bucket, Duration ttl) {
		this.s3 = s3;
		this.presigner = presigner;
		this.bucket = bucket;
		this.ttl = ttl;
	}

	@Override
	public PresignedUpload presignUpload(String key, String contentType, long sizeBytes) {
		PresignedPutObjectRequest presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
				.signatureDuration(ttl)
				.putObjectRequest(PutObjectRequest.builder()
						.bucket(bucket)
						.key(key)
						.contentType(contentType)
						.contentLength(sizeBytes)
						.build())
				.build());
		Map<String, String> headers = new HashMap<>();
		presigned.signedHeaders().forEach((name, values) -> {
			if (!name.equalsIgnoreCase("host")) {
				headers.put(name, String.join(",", values));
			}
		});
		return new PresignedUpload(toUri(presigned.url()), "PUT", headers, presigned.expiration());
	}

	@Override
	public PresignedDownload presignDownload(String key, String fileName) {
		var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
				.signatureDuration(ttl)
				.getObjectRequest(GetObjectRequest.builder()
						.bucket(bucket)
						.key(key)
						.responseContentDisposition("attachment; filename=\"" + fileName.replace("\"", "") + "\"")
						.build())
				.build());
		return new PresignedDownload(toUri(presigned.url()), presigned.expiration());
	}

	@Override
	public boolean exists(String key) {
		try {
			s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
			return true;
		}
		catch (NoSuchKeyException ex) {
			return false;
		}
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
				RequestBody.fromBytes(content));
	}

	private static URI toUri(java.net.URL url) {
		return URI.create(url.toString());
	}

}
