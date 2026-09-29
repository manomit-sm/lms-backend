package com.bsolz.lms.leave.repository;

import com.bsolz.lms.leave.entity.LeaveAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveAttachmentRepository extends JpaRepository<LeaveAttachment, UUID> {

	List<LeaveAttachment> findAllByLeaveRequestIdOrderByCreatedAtAsc(UUID leaveRequestId);

	Optional<LeaveAttachment> findByIdAndLeaveRequestId(UUID id, UUID leaveRequestId);

}
