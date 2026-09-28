package com.bsolz.lms.leavepolicy.entity;

import com.bsolz.lms.leavepolicy.model.enums.LeavePeriodStatus;
import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** The span balances are allocated for, usually a leave year. Whole months, never overlapping. */
@Getter
@Setter
@Entity
@Table(name = "leave_period")
public class LeavePeriod extends BaseEntity {

	private String name;

	private LocalDate startDate;

	/** Inclusive. */
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	private LeavePeriodStatus status = LeavePeriodStatus.OPEN;

}
