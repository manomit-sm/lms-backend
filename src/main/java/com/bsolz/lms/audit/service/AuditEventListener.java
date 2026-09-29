package com.bsolz.lms.audit.service;

import com.bsolz.lms.approval.api.ApprovalTaskDecided;
import com.bsolz.lms.approval.api.ApprovalTaskEscalated;
import com.bsolz.lms.balance.api.BalanceAdjusted;
import com.bsolz.lms.balance.api.LeavePeriodRolledOver;
import com.bsolz.lms.leave.api.LeaveRequestStatusChanged;
import com.bsolz.lms.organization.api.EmployeeCreated;
import com.bsolz.lms.organization.api.EmployeeExited;
import com.bsolz.lms.shared.tenancy.TenantExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Feeds the audit trail. Asynchronous after the publishing transaction commits, so an audit failure
 * never affects the business change; Spring Modulith records each delivery and a failed one is
 * resubmitted by the event resubmission job. The tenant is bound from the event.
 */
@Component
@RequiredArgsConstructor
class AuditEventListener {

	private final TenantExecutor tenantExecutor;

	private final AuditRecorder recorder;

	@Async
	@TransactionalEventListener
	void on(EmployeeCreated event) {
		tenantExecutor.run(event.tenantId(), () -> recorder.employeeCreated(event));
	}

	@Async
	@TransactionalEventListener
	void on(EmployeeExited event) {
		tenantExecutor.run(event.tenantId(), () -> recorder.employeeExited(event));
	}

	@Async
	@TransactionalEventListener
	void on(LeaveRequestStatusChanged event) {
		tenantExecutor.run(event.tenantId(), () -> recorder.leaveStatusChanged(event));
	}

	@Async
	@TransactionalEventListener
	void on(ApprovalTaskDecided event) {
		tenantExecutor.run(event.tenantId(), () -> recorder.approvalTaskDecided(event));
	}

	@Async
	@TransactionalEventListener
	void on(ApprovalTaskEscalated event) {
		tenantExecutor.run(event.tenantId(), () -> recorder.approvalTaskEscalated(event));
	}

	@Async
	@TransactionalEventListener
	void on(BalanceAdjusted event) {
		tenantExecutor.run(event.tenantId(), () -> recorder.balanceAdjusted(event));
	}

	@Async
	@TransactionalEventListener
	void on(LeavePeriodRolledOver event) {
		tenantExecutor.run(event.tenantId(), () -> recorder.leavePeriodRolledOver(event));
	}

}
