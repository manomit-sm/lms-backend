package com.bsolz.lms.balance.service;

import com.bsolz.lms.organization.api.EmployeeCreated;
import com.bsolz.lms.shared.tenancy.TenantExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gives new employees their balances. Asynchronous after the employee transaction commits, recorded by
 * Spring Modulith (a failure stays incomplete for resubmission); allocation is idempotent, so a
 * resubmitted event never allocates twice.
 */
@Component
@RequiredArgsConstructor
class EmployeeJoinedListener {

	private final TenantExecutor tenantExecutor;

	private final AllocationService allocationService;

	@Async
	@TransactionalEventListener
	void on(EmployeeCreated event) {
		tenantExecutor.run(event.tenantId(), () -> allocationService.allocateForNewEmployee(event.employeeId()));
	}

}
