package com.bsolz.lms.leave.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.bsolz.lms.leave.model.enums.LeaveAction;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class LeaveStateMachineTest {

	@ParameterizedTest(name = "{0} --{1}--> {2}")
	@CsvSource({ "PENDING, APPROVE, APPROVED", "PENDING, REJECT, REJECTED", "PENDING, WITHDRAW, WITHDRAWN",
			"APPROVED, CANCEL, CANCELLED", "APPROVED, REQUEST_CANCELLATION, CANCELLATION_PENDING",
			"CANCELLATION_PENDING, APPROVE_CANCELLATION, CANCELLED",
			"CANCELLATION_PENDING, REJECT_CANCELLATION, APPROVED" })
	void allowsTheDocumentedTransitions(LeaveStatus from, LeaveAction action, LeaveStatus to) {
		assertThat(LeaveStateMachine.next(from, action)).hasValue(to);
	}

	@ParameterizedTest(name = "{0} --{1}--> refused")
	@CsvSource({ "PENDING, CANCEL", "APPROVED, APPROVE", "APPROVED, WITHDRAW", "REJECTED, APPROVE",
			"CANCELLATION_PENDING, CANCEL", "CANCELLATION_PENDING, WITHDRAW" })
	void refusesEverythingElse(LeaveStatus from, LeaveAction action) {
		assertThat(LeaveStateMachine.next(from, action)).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = LeaveStatus.class, names = { "REJECTED", "WITHDRAWN", "CANCELLED" })
	void finalStatusesAreFinal(LeaveStatus status) {
		for (LeaveAction action : LeaveAction.values()) {
			assertThat(LeaveStateMachine.next(status, action)).isEmpty();
		}
	}

}
