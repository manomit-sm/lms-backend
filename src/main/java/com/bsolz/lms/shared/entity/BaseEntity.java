package com.bsolz.lms.shared.entity;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.Hibernate;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Common columns for every table: UUID primary key, audit timestamps/users and an optimistic-lock
 * version. {@code createdBy}/{@code updatedBy} hold the acting user's id (see
 * {@code SecurityAuditorAware}); they stay null for system and platform-admin actions.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@CreatedDate
	private Instant createdAt;

	@CreatedBy
	private UUID createdBy;

	@LastModifiedDate
	private Instant updatedAt;

	@LastModifiedBy
	private UUID updatedBy;

	@Version
	private long version;

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
			return false;
		}
		return getId() != null && getId().equals(((BaseEntity) other).getId());
	}

	@Override
	public int hashCode() {
		return Hibernate.getClass(this).hashCode();
	}

}
