package com.bsolz.lms.reporting.service;

import com.bsolz.lms.reporting.api.ReportExportRequested;
import com.bsolz.lms.shared.tenancy.TenantExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Generates exports asynchronously once the request has committed. Recorded by Spring Modulith, so an
 * export interrupted by a crash is picked up again by the event resubmission job.
 */
@Component
@RequiredArgsConstructor
class ReportExportListener {

	private final TenantExecutor tenantExecutor;

	private final ReportExportService exportService;

	@Async
	@TransactionalEventListener
	void on(ReportExportRequested event) {
		tenantExecutor.run(event.tenantId(), () -> exportService.generate(event.exportId()));
	}

}
