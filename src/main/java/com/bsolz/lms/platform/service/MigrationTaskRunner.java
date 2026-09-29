package com.bsolz.lms.platform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The {@code migrate} profile runs the application as a one-off migration task (a deployment step before
 * the new version rolls out): migrations run while the context starts ({@link TenantMigrationService}),
 * then this exits. A public-schema failure fails startup, so the task exits non-zero and the rollout
 * stops; a failed tenant is only logged, as it doesn't block the other tenants.
 */
@Slf4j
@Component
@Profile("migrate")
@RequiredArgsConstructor
class MigrationTaskRunner implements ApplicationRunner {

	private final ApplicationContext context;

	@Override
	public void run(ApplicationArguments args) {
		log.info("Migrations finished; exiting");
		System.exit(SpringApplication.exit(context, () -> 0));
	}

}
