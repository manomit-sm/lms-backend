package com.bsolz.lms.approval.repository;

import com.bsolz.lms.approval.entity.ApprovalRequest;
import com.bsolz.lms.approval.model.enums.ApprovalStatus;
import com.bsolz.lms.approval.model.enums.ApprovalSubjectType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

	List<ApprovalRequest> findAllBySubjectIdOrderByCreatedAtAsc(UUID subjectId);

	Optional<ApprovalRequest> findBySubjectTypeAndSubjectIdAndStatus(ApprovalSubjectType subjectType, UUID subjectId,
			ApprovalStatus status);

	/** Serializes decisions on one approval (two assignees acting at once). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from ApprovalRequest r where r.id = :id")
	Optional<ApprovalRequest> findByIdForUpdate(@Param("id") UUID id);

}
