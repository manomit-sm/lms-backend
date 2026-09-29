package com.bsolz.lms.balance.repository;

import com.bsolz.lms.balance.entity.LeaveBalanceTransaction;
import com.bsolz.lms.balance.model.enums.BalanceReferenceType;
import com.bsolz.lms.balance.model.enums.BalanceTransactionType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveBalanceTransactionRepository extends JpaRepository<LeaveBalanceTransaction, UUID> {

	Page<LeaveBalanceTransaction> findAllByLeaveBalanceIdOrderBySeqDesc(UUID leaveBalanceId, Pageable pageable);

	List<LeaveBalanceTransaction> findAllByReferenceId(UUID referenceId);

	boolean existsByLeaveBalanceIdAndType(UUID leaveBalanceId, BalanceTransactionType type);

	boolean existsByLeaveBalanceIdAndReferenceType(UUID leaveBalanceId, BalanceReferenceType referenceType);

	long countByLeaveBalanceIdAndType(UUID leaveBalanceId, BalanceTransactionType type);

}
