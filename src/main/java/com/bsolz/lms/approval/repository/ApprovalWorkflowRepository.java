package com.bsolz.lms.approval.repository;

import com.bsolz.lms.approval.entity.ApprovalWorkflow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, UUID> {

	@EntityGraph(attributePaths = { "rules", "steps" })
	List<ApprovalWorkflow> findAllByOrderByPriorityAsc();

	@EntityGraph(attributePaths = { "rules", "steps" })
	Optional<ApprovalWorkflow> findWithDetailsById(UUID id);

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

	boolean existsByPriority(int priority);

	boolean existsByPriorityAndIdNot(int priority, UUID id);

}
