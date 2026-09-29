package com.bsolz.lms.leave.repository;

import com.bsolz.lms.leave.entity.LeaveRequestHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRequestHistoryRepository extends JpaRepository<LeaveRequestHistory, UUID> {

	List<LeaveRequestHistory> findAllByLeaveRequestIdOrderByCreatedAtAsc(UUID leaveRequestId);

}
