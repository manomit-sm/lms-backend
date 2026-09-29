package com.bsolz.lms.approval.repository;

import com.bsolz.lms.approval.entity.ApprovalTask;
import com.bsolz.lms.approval.model.enums.ApprovalTaskStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, UUID> {

	@Query("""
			select t from ApprovalTask t join fetch t.approvalRequest
			where t.status = :status and :userId member of t.assigneeUserIds
			order by t.updatedAt
			""")
	List<ApprovalTask> findAssignedWithStatus(@Param("userId") UUID userId, @Param("status") ApprovalTaskStatus status);

	@Query("""
			select t.id from ApprovalTask t join t.approvalRequest r
			where t.status = com.bsolz.lms.approval.model.enums.ApprovalTaskStatus.PENDING
			  and (r.reminderAfterHours is not null or r.escalateAfterHours is not null
			       or r.autoApproveAfterHours is not null)
			order by t.activatedAt
			""")
	List<UUID> findPendingIdsWithDeadlines();

	@Query("select t.approvalRequest.id from ApprovalTask t where t.id = :taskId")
	Optional<UUID> findApprovalIdByTaskId(@Param("taskId") UUID taskId);

	@Query("""
			select count(t) > 0 from ApprovalTask t
			where t.approvalRequest.subjectId = :subjectId and :userId member of t.assigneeUserIds
			""")
	boolean isAssigneeForSubject(@Param("userId") UUID userId, @Param("subjectId") UUID subjectId);

}
