package com.bsolz.lms.balance.entity;

import com.bsolz.lms.balance.model.enums.BalanceTransactionType;
import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One employee's balance of one leave type for one leave period. Only changed through
 * {@link #apply}, which the ledger calls together with writing the matching ledger row; the
 * database's generated {@code available} column uses the same formula as {@link #getAvailable()}.
 */
@Getter
@Entity
@Table(name = "leave_balance")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveBalance extends BaseEntity {

	private UUID employeeId;

	private UUID leaveTypeId;

	private UUID leavePeriodId;

	private BigDecimal allocated;

	private BigDecimal carriedForward;

	private BigDecimal adjusted;

	private BigDecimal expired;

	private BigDecimal carriedOut;

	private BigDecimal used;

	private BigDecimal pending;

	public BigDecimal getAvailable() {
		return allocated.add(carriedForward).add(adjusted).subtract(expired).subtract(carriedOut).subtract(used)
				.subtract(pending);
	}

	public void apply(BalanceTransactionType type, BigDecimal amount) {
		switch (type) {
			case ALLOCATION, ACCRUAL -> allocated = allocated.add(amount);
			case CARRY_FORWARD -> carriedForward = carriedForward.add(amount);
			case ADJUSTMENT -> adjusted = adjusted.add(amount);
			case EXPIRY -> expired = expired.add(amount);
			case CARRY_OUT -> carriedOut = carriedOut.add(amount);
			case HOLD -> pending = pending.add(amount);
			case RELEASE -> pending = pending.subtract(amount);
			case CONSUME -> {
				pending = pending.subtract(amount);
				used = used.add(amount);
			}
			case REVERSAL -> used = used.subtract(amount);
		}
		if (pending.signum() < 0 || used.signum() < 0) {
			throw new IllegalStateException(type + " of " + amount + " would make balance " + getId() + " negative");
		}
	}

}
