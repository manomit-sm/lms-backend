package com.bsolz.lms.leavepolicy.repository;

import com.bsolz.lms.leavepolicy.entity.LeavePolicy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, UUID> {

	@EntityGraph(attributePaths = { "leaveType", "appliesTo" })
	List<LeavePolicy> findAllByOrderByNameAsc();

	@EntityGraph(attributePaths = { "leaveType", "appliesTo" })
	List<LeavePolicy> findAllByLeaveTypeIdOrderByNameAsc(UUID leaveTypeId);

	@EntityGraph(attributePaths = { "leaveType", "appliesTo" })
	Optional<LeavePolicy> findWithRulesById(UUID id);

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

}
