package com.bsolz.lms.identity.service;

import com.bsolz.lms.organization.api.EmployeeCreated;
import com.bsolz.lms.organization.api.EmployeeExited;
import com.bsolz.lms.shared.tenancy.TenantExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps users in step with employees. Runs asynchronously after the employee transaction commits;
 * Spring Modulith records each delivery, so a failure (e.g. the identity provider being down) leaves
 * the event incomplete for resubmission instead of losing it. The tenant is bound from the event
 * itself before any transaction starts.
 */
@Component
@RequiredArgsConstructor
class EmployeeEventListener {

	private final TenantExecutor tenantExecutor;

	private final UserAccountService accountService;

	@Async
	@TransactionalEventListener
	void on(EmployeeCreated event) {
		tenantExecutor.run(event.tenantId(),
				() -> accountService.provisionForEmployee(event.employeeId(), event.email()));
	}

	@Async
	@TransactionalEventListener
	void on(EmployeeExited event) {
		tenantExecutor.run(event.tenantId(), () -> accountService.disableForExitedEmployee(event.employeeId()));
	}

}
