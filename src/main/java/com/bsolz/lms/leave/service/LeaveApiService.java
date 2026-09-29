package com.bsolz.lms.leave.service;

import com.bsolz.lms.leave.api.LeaveApi;
import com.bsolz.lms.leave.api.LeaveSummary;
import com.bsolz.lms.leave.entity.LeaveRequest;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import com.bsolz.lms.leave.repository.LeaveRequestRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class LeaveApiService implements LeaveApi {

	private final LeaveRequestRepository repository;

	@Override
	public Optional<LeaveSummary> findLeave(UUID leaveRequestId) {
		return repository.findWithDaysById(leaveRequestId).map(LeaveApiService::toSummary);
	}

	@Override
	public List<LeaveSummary> findLeaves(Collection<UUID> employeeIds, LocalDate from, LocalDate to,
			Set<LeaveStatus> statuses) {
		if (employeeIds.isEmpty() || statuses.isEmpty()) {
			return List.of();
		}
		return repository.findOverlapping(employeeIds, from, to, statuses).stream()
				.map(LeaveApiService::toSummary)
				.toList();
	}

	@Override
	public List<LeaveSummary> findLeavesStartingOn(LocalDate date, Set<LeaveStatus> statuses) {
		return repository.findAllByStartDateAndStatusIn(date, statuses).stream().map(LeaveApiService::toSummary).toList();
	}

	private static LeaveSummary toSummary(LeaveRequest request) {
		return new LeaveSummary(request.getId(), request.getEmployeeId(), request.getLeaveTypeId(),
				request.getStartDate(), request.getEndDate(), request.getStartSession(), request.getEndSession(),
				request.getTotalDays(), request.getStatus(),
				request.getDays().stream()
						.map(day -> new LeaveSummary.Day(day.date(), day.dayType(), day.session(), day.amount()))
						.toList());
	}

}
