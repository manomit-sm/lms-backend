package com.bsolz.lms.balance.service;

import com.bsolz.lms.balance.entity.LeaveBalance;
import com.bsolz.lms.balance.entity.LeaveBalanceTransaction;
import com.bsolz.lms.balance.exception.BalanceErrorCode;
import com.bsolz.lms.balance.model.enums.BalanceReferenceType;
import com.bsolz.lms.balance.model.enums.BalanceTransactionType;
import com.bsolz.lms.balance.repository.LeaveBalanceRepository;
import com.bsolz.lms.balance.repository.LeaveBalanceTransactionRepository;
import com.bsolz.lms.shared.exception.ApiException;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way balances change: lock the balance row, apply the change, append the ledger row - all in
 * the caller's transaction. Holding the row lock until commit serializes concurrent changes to one
 * balance, which is what stops two requests from spending the same days.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
class BalanceLedger {

	private final LeaveBalanceRepository balanceRepository;

	private final LeaveBalanceTransactionRepository transactionRepository;

	/** Locks the balance, creating it (all zero) first if needed. */
	LeaveBalance lock(UUID employeeId, UUID leaveTypeId, UUID leavePeriodId) {
		balanceRepository.insertIfAbsent(employeeId, leaveTypeId, leavePeriodId);
		return balanceRepository.findForUpdate(employeeId, leaveTypeId, leavePeriodId)
				.orElseThrow(() -> new IllegalStateException("Balance vanished after insert"));
	}

	LeaveBalance lock(UUID balanceId) {
		return balanceRepository.findByIdForUpdate(balanceId)
				.orElseThrow(() -> new ApiException(BalanceErrorCode.BALANCE_NOT_FOUND, "Balance not found"));
	}

	/** Applies the change to a balance locked by this transaction and records it. */
	LeaveBalanceTransaction post(LeaveBalance balance, BalanceTransactionType type, BigDecimal amount,
			BalanceReferenceType referenceType, UUID referenceId, String note) {
		balance.apply(type, amount);
		return transactionRepository
				.save(new LeaveBalanceTransaction(balance, type, amount, referenceType, referenceId, note));
	}

}
