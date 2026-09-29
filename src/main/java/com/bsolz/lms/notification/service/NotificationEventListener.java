package com.bsolz.lms.notification.service;

import com.bsolz.lms.approval.api.ApprovalTaskAssigned;
import com.bsolz.lms.approval.api.ApprovalTaskEscalated;
import com.bsolz.lms.approval.api.ApprovalTaskReminded;
import com.bsolz.lms.balance.api.BalanceAdjusted;
import com.bsolz.lms.leave.api.LeaveRequestStatusChanged;
import com.bsolz.lms.shared.tenancy.TenantExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notifies people about domain events. Asynchronous after the publishing transaction commits, so a
 * notification failure never affects the business change; Spring Modulith records each delivery and a
 * failed one is resubmitted by the event resubmission job. Notifications are keyed by the event, so a
 * redelivery never notifies twice. The tenant is bound from the event.
 */
@Component
@RequiredArgsConstructor
class NotificationEventListener {

	private final TenantExecutor tenantExecutor;

	private final NotificationPublisher publisher;

	@Async
	@TransactionalEventListener
	void on(LeaveRequestStatusChanged event) {
		tenantExecutor.run(event.tenantId(), () -> publisher.leaveStatusChanged(event));
	}

	@Async
	@TransactionalEventListener
	void on(ApprovalTaskAssigned event) {
		tenantExecutor.run(event.tenantId(), () -> publisher.approvalTaskAssigned(event));
	}

	@Async
	@TransactionalEventListener
	void on(ApprovalTaskReminded event) {
		tenantExecutor.run(event.tenantId(), () -> publisher.approvalTaskReminded(event));
	}

	@Async
	@TransactionalEventListener
	void on(ApprovalTaskEscalated event) {
		tenantExecutor.run(event.tenantId(), () -> publisher.approvalTaskEscalated(event));
	}

	@Async
	@TransactionalEventListener
	void on(BalanceAdjusted event) {
		tenantExecutor.run(event.tenantId(), () -> publisher.balanceAdjusted(event));
	}

}
