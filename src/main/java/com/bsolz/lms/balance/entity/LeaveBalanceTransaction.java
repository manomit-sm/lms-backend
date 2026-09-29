package com.bsolz.lms.balance.entity;

import com.bsolz.lms.balance.model.enums.BalanceReferenceType;
import com.bsolz.lms.balance.model.enums.BalanceTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** A ledger entry. Append-only: never updated or deleted (a database trigger rejects both). */
@Getter
@Entity
@Immutable
@Table(name = "leave_balance_transaction")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveBalanceTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/** Insertion order, assigned by the database. */
	@Column(insertable = false, updatable = false)
	private Long seq;

	private UUID leaveBalanceId;

	@Enumerated(EnumType.STRING)
	private BalanceTransactionType type;

	private BigDecimal amount;

	/** The balance's available days right after this entry. */
	private BigDecimal availableAfter;

	@Enumerated(EnumType.STRING)
	private BalanceReferenceType referenceType;

	private UUID referenceId;

	private String note;

	@CreatedDate
	private Instant createdAt;

	@CreatedBy
	private UUID createdBy;

	public LeaveBalanceTransaction(LeaveBalance balance, BalanceTransactionType type, BigDecimal amount,
			BalanceReferenceType referenceType, UUID referenceId, String note) {
		this.leaveBalanceId = balance.getId();
		this.type = type;
		this.amount = amount;
		this.availableAfter = balance.getAvailable();
		this.referenceType = referenceType;
		this.referenceId = referenceId;
		this.note = note;
	}

}
