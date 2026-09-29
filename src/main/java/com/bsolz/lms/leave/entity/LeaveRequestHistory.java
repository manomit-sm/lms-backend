package com.bsolz.lms.leave.entity;

import com.bsolz.lms.leave.model.enums.LeaveAction;
import com.bsolz.lms.leave.model.enums.LeaveStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** One status change of a leave request. Append-only. */
@Getter
@Entity
@Immutable
@Table(name = "leave_request_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequestHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID leaveRequestId;

	@Enumerated(EnumType.STRING)
	private LeaveStatus fromStatus;

	@Enumerated(EnumType.STRING)
	private LeaveStatus toStatus;

	@Enumerated(EnumType.STRING)
	private LeaveAction action;

	/** Null when the change happened automatically. */
	private UUID actorUserId;

	private String comment;

	private Instant createdAt;

	public LeaveRequestHistory(UUID leaveRequestId, LeaveStatus fromStatus, LeaveStatus toStatus, LeaveAction action,
			UUID actorUserId, String comment, Instant createdAt) {
		this.leaveRequestId = leaveRequestId;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.action = action;
		this.actorUserId = actorUserId;
		this.comment = comment;
		this.createdAt = createdAt;
	}

}
