package com.bsolz.lms.balance.repository;

import com.bsolz.lms.balance.entity.LeaveBalance;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

	/** Creates an all-zero balance unless one exists; waits for a concurrent insert of the same balance. */
	@Modifying
	@Query(value = """
			INSERT INTO leave_balance (employee_id, leave_type_id, leave_period_id)
			VALUES (:employeeId, :leaveTypeId, :leavePeriodId)
			ON CONFLICT (employee_id, leave_type_id, leave_period_id) DO NOTHING
			""", nativeQuery = true)
	void insertIfAbsent(@Param("employeeId") UUID employeeId, @Param("leaveTypeId") UUID leaveTypeId,
			@Param("leavePeriodId") UUID leavePeriodId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select b from LeaveBalance b
			where b.employeeId = :employeeId and b.leaveTypeId = :leaveTypeId and b.leavePeriodId = :leavePeriodId
			""")
	Optional<LeaveBalance> findForUpdate(@Param("employeeId") UUID employeeId, @Param("leaveTypeId") UUID leaveTypeId,
			@Param("leavePeriodId") UUID leavePeriodId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from LeaveBalance b where b.id = :id")
	Optional<LeaveBalance> findByIdForUpdate(@Param("id") UUID id);

	Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndLeavePeriodId(UUID employeeId, UUID leaveTypeId,
			UUID leavePeriodId);

	List<LeaveBalance> findAllByEmployeeIdAndLeavePeriodId(UUID employeeId, UUID leavePeriodId);

}
