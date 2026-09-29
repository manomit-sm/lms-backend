package com.bsolz.lms.shared.tenancy;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Maintains Spring Modulith's event publication registry, which lives in {@code public} and in every
 * tenant schema: each run covers {@code public} (no tenant bound) and then every serving tenant, with the
 * tenant bound so the registry reads and writes that tenant's table. Modulith's own restart
 * resubmission is off ({@code republish-outstanding-events-on-restart}) because it would only see
 * {@code public}.
 * <ul>
 * <li>Resubmission: deliveries still incomplete after {@value #RESUBMIT_AFTER_MINUTES} minutes (a failed
 * listener, or an instance that died mid-delivery) are delivered again. Listeners are idempotent, so a
 * delivery that was only slow is harmless.</li>
 * <li>Cleanup: completed publications are deleted after {@value #KEEP_COMPLETED_DAYS} days.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublicationJobs {

	static final String EVENT_RESUBMISSION = "event-resubmission";

	static final String EVENT_PUBLICATION_CLEANUP = "event-publication-cleanup";

	static final int RESUBMIT_AFTER_MINUTES = 5;

	static final int KEEP_COMPLETED_DAYS = 7;

	private final TenantJobRunner jobRunner;

	private final IncompleteEventPublications incompletePublications;

	private final CompletedEventPublications completedPublications;

	@Scheduled(cron = "${lms.jobs.event-resubmission}", zone = "UTC")
	@SchedulerLock(name = EVENT_RESUBMISSION, lockAtMostFor = "PT10M")
	void resubmitIncomplete() {
		resubmitIncomplete(Duration.ofMinutes(RESUBMIT_AFTER_MINUTES));
	}

	@Scheduled(cron = "${lms.jobs.event-publication-cleanup}", zone = "UTC")
	@SchedulerLock(name = EVENT_PUBLICATION_CLEANUP, lockAtMostFor = "PT30M")
	void deleteCompleted() {
		Duration age = Duration.ofDays(KEEP_COMPLETED_DAYS);
		completedPublications.deletePublicationsOlderThan(age);
		jobRunner.runForEachTenant(EVENT_PUBLICATION_CLEANUP,
				tenant -> completedPublications.deletePublicationsOlderThan(age));
	}

	/** Resubmits incomplete deliveries published more than {@code olderThan} ago, in public and every tenant. */
	public TenantJobRunner.JobRunResult resubmitIncomplete(Duration olderThan) {
		try {
			incompletePublications.resubmitIncompletePublicationsOlderThan(olderThan);
		}
		catch (RuntimeException ex) {
			log.error("Resubmitting incomplete event publications in public failed", ex);
		}
		return jobRunner.runForEachTenant(EVENT_RESUBMISSION,
				tenant -> incompletePublications.resubmitIncompletePublicationsOlderThan(olderThan));
	}

}
