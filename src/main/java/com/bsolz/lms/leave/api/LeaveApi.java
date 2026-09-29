package com.bsolz.lms.leave.api;

import com.bsolz.lms.leave.model.enums.LeaveStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read access to leave requests for other modules. All calls need a tenant bound. */
public interface LeaveApi {

	Optional<LeaveSummary> findLeave(UUID leaveRequestId);

	/**
	 * The employees' requests in one of the statuses that overlap the dates (inclusive), ordered by start
	 * date.
	 */
	List<LeaveSummary> findLeaves(Collection<UUID> employeeIds, LocalDate from, LocalDate to,
			Set<LeaveStatus> statuses);

	/** Requests in one of the statuses that start on the date. */
	List<LeaveSummary> findLeavesStartingOn(LocalDate date, Set<LeaveStatus> statuses);

}
