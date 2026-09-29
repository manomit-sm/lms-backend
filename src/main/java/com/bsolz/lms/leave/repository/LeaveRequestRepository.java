package com.bsolz.lms.leave.repository;

import com.bsolz.lms.leave.entity.LeaveRequest;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID>,
		JpaSpecificationExecutor<LeaveRequest> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from LeaveRequest r where r.id = :id")
	Optional<LeaveRequest> findByIdForUpdate(@Param("id") UUID id);

	@Query("""
			select count(r) > 0 from LeaveRequest r
			where r.employeeId = :employeeId and r.status in :statuses
			  and r.startDate <= :endDate and r.endDate >= :startDate
			""")
	boolean existsOverlapping(@Param("employeeId") UUID employeeId, @Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate, @Param("statuses") Collection<LeaveStatus> statuses);

	List<LeaveRequest> findAllByIdIn(Collection<UUID> ids);

	@EntityGraph(attributePaths = "days")
	@Query("""
			select distinct r from LeaveRequest r
			where r.employeeId in :employeeIds and r.status in :statuses
			  and r.startDate <= :to and r.endDate >= :from
			order by r.startDate, r.id
			""")
	List<LeaveRequest> findOverlapping(@Param("employeeIds") Collection<UUID> employeeIds,
			@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("statuses") Collection<LeaveStatus> statuses);

	@EntityGraph(attributePaths = "days")
	List<LeaveRequest> findAllByStartDateAndStatusIn(LocalDate startDate, Collection<LeaveStatus> statuses);

	@EntityGraph(attributePaths = "days")
	Optional<LeaveRequest> findWithDaysById(UUID id);

}
