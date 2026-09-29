package com.bsolz.lms.leave.entity;

import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A supporting document. Created when its upload is requested; linked to a request on submission. */
@Getter
@Entity
@Table(name = "leave_attachment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveAttachment extends BaseEntity {

	/** Null until the request it belongs to is submitted. */
	private UUID leaveRequestId;

	private UUID uploadedByUserId;

	private String fileName;

	private String contentType;

	private long sizeBytes;

	private String storageKey;

	public LeaveAttachment(UUID uploadedByUserId, String fileName, String contentType, long sizeBytes,
			String storageKey) {
		this.uploadedByUserId = uploadedByUserId;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.storageKey = storageKey;
	}

	public void linkTo(UUID requestId) {
		this.leaveRequestId = requestId;
	}

}
