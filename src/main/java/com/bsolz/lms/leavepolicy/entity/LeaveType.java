package com.bsolz.lms.leavepolicy.entity;

import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A kind of leave (annual, sick, ...). Deactivated rather than deleted: requests and balances refer to it. */
@Getter
@Setter
@Entity
@Table(name = "leave_type")
public class LeaveType extends BaseEntity {

	/** Upper case, e.g. {@code ANNUAL}. */
	private String code;

	private String name;

	private String description;

	/** {@code #RRGGBB}, for calendars and charts. */
	private String color;

	private boolean paid;

	private boolean balanceTracked;

	/** Whether taking it means being away from work; false for e.g. work from home. */
	private boolean timeOff = true;

	private boolean halfDayAllowed;

	private boolean active = true;

	private int sortOrder;

}
