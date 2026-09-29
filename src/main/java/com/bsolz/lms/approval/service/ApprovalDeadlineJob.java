package com.bsolz.lms.approval.service;

import com.bsolz.lms.shared.tenancy.TenantJobRunner;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reminds, escalates and automatically approves overdue approval steps (see
 * {@link ApprovalService#applyDeadlines}), for every tenant, once across all instances. Each step is
 * handled in its own transaction under the approval's lock, so it never races a human decision.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalDeadlineJob {

	static final String APPROVAL_DEADLINES = "approval-deadlines";

	private final TenantJobRunner jobRunner;

	private final ApprovalService approvalService;

	private final Clock clock;

	@Scheduled(cron = "${lms.jobs.approval-deadlines}", zone = "UTC")
	@SchedulerLock(name = APPROVAL_DEADLINES, lockAtMostFor = "PT30M")
	void applyDeadlines() {
		jobRunner.runForEachTenant(APPROVAL_DEADLINES, tenant -> run(Instant.now(clock)));
	}

	/** One pass over the bound tenant's pending steps; a failing step is logged and skipped. */
	public Map<ApprovalService.DeadlineAction, Integer> run(Instant now) {
		Map<ApprovalService.DeadlineAction, Integer> counts = new EnumMap<>(ApprovalService.DeadlineAction.class);
		for (UUID taskId : approvalService.findTasksWithDeadlines()) {
			try {
				counts.merge(approvalService.applyDeadlines(taskId, now), 1, Integer::sum);
			}
			catch (RuntimeException ex) {
				log.error("Applying deadlines to approval task {} failed", taskId, ex);
			}
		}
		return counts;
	}

}
