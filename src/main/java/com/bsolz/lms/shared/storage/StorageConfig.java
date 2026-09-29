package com.bsolz.lms.shared.storage;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Picks the {@link FileStorage} by {@code lms.storage.provider}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfig {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(name = "lms.storage.provider", havingValue = "s3")
	static class S3StorageConfig {

		@Bean(destroyMethod = "close")
		S3Client s3Client(StorageProperties properties) {
			return S3Client.builder().region(Region.of(properties.s3().region())).build();
		}

		@Bean(destroyMethod = "close")
		S3Presigner s3Presigner(StorageProperties properties) {
			return S3Presigner.builder().region(Region.of(properties.s3().region())).build();
		}

		@Bean
		FileStorage fileStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
			return new S3FileStorage(s3, presigner, properties.s3().bucket(), properties.presignTtl());
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(name = "lms.storage.provider", havingValue = "fake")
	static class InMemoryStorageConfig {

		@Bean
		InMemoryFileStorage fileStorage(StorageProperties properties, Clock clock) {
			return new InMemoryFileStorage(properties.presignTtl(), clock);
		}

	}

}
