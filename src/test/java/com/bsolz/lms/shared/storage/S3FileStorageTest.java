package com.bsolz.lms.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Presigning is local signing, so this runs without AWS: static credentials, no network. */
class S3FileStorageTest {

	private static final String KEY = "tenants/t-1/leave-attachments/a-1/note.pdf";

	private final S3Client s3 = mock(S3Client.class);

	private final S3Presigner presigner = S3Presigner.builder()
			.region(Region.EU_WEST_1)
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIDEXAMPLE", "secret")))
			.build();

	private final S3FileStorage storage = new S3FileStorage(s3, presigner, "lms-files", Duration.ofMinutes(10));

	@AfterEach
	void close() {
		presigner.close();
	}

	@Test
	void uploadUrlIsSignedForTheKeyTypeAndSize() {
		PresignedUpload upload = storage.presignUpload(KEY, "application/pdf", 2048);

		assertThat(upload.method()).isEqualTo("PUT");
		assertThat(upload.url().getHost()).startsWith("lms-files.s3.");
		assertThat(upload.url().getPath()).isEqualTo("/" + KEY);
		assertThat(upload.url().getQuery()).contains("X-Amz-Signature=", "X-Amz-Expires=600");
		assertThat(upload.headers()).containsEntry("content-type", "application/pdf")
				.containsEntry("content-length", "2048")
				.doesNotContainKey("host");
	}

	@Test
	void downloadUrlNamesTheFile() {
		PresignedDownload download = storage.presignDownload(KEY, "Doctor's \"note\".pdf");

		assertThat(URLDecoder.decode(download.url().getRawQuery(), StandardCharsets.UTF_8))
				.contains("response-content-disposition=attachment; filename=\"Doctor's note.pdf\"");
	}

	@Test
	void existsAsksS3() {
		when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build())
				.thenThrow(NoSuchKeyException.builder().build());

		assertThat(storage.exists(KEY)).isTrue();
		assertThat(storage.exists(KEY)).isFalse();
	}

}
