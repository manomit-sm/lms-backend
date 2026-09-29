package com.bsolz.lms.leave.entity;

import com.bsolz.lms.leave.model.enums.DaySession;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A leave request. Its status only changes through {@code LeaveRequestService}, which checks the
 * transition against the state machine and records it in the history.
 */
@Getter
@Entity
@Table(name = "leave_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequest extends BaseEntity {

	private UUID employeeId;

	private UUID leaveTypeId;

	private UUID leavePeriodId;

	private LocalDate startDate;

	/** Inclusive. */
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	private DaySession startSession;

	@Enumerated(EnumType.STRING)
	private DaySession endSession;

	private BigDecimal totalDays;

	private String reason;

	@Enumerated(EnumType.STRING)
	private LeaveStatus status;

	private Instant decidedAt;

	private String cancellationReason;

	@ElementCollection
	@CollectionTable(name = "leave_request_day", joinColumns = @JoinColumn(name = "leave_request_id"))
	@OrderBy("date")
	private List<LeaveDay> days = new ArrayList<>();

	public LeaveRequest(UUID employeeId, UUID leaveTypeId, UUID leavePeriodId, LocalDate startDate, LocalDate endDate,
			DaySession startSession, DaySession endSession, List<LeaveDay> days, BigDecimal totalDays, String reason) {
		this.employeeId = employeeId;
		this.leaveTypeId = leaveTypeId;
		this.leavePeriodId = leavePeriodId;
		this.startDate = startDate;
		this.endDate = endDate;
		this.startSession = startSession;
		this.endSession = endSession;
		this.days.addAll(days);
		this.totalDays = totalDays;
		this.reason = reason;
		this.status = LeaveStatus.PENDING;
	}

	/** Only for {@code LeaveRequestService}, after checking the transition. */
	public void moveTo(LeaveStatus newStatus, Instant at) {
		if (status == LeaveStatus.PENDING && newStatus != LeaveStatus.WITHDRAWN) {
			decidedAt = at;
		}
		status = newStatus;
	}

	public void setCancellationReason(String cancellationReason) {
		this.cancellationReason = cancellationReason;
	}

}
