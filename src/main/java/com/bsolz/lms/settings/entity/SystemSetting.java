package com.bsolz.lms.settings.entity;

import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import lombok.Getter;
import lombok.Setter;

/** The tenant's settings: exactly one row per tenant schema, created by the tenant changelog. */
@Getter
@Setter
@Entity
@Table(name = "system_setting")
public class SystemSetting extends BaseEntity {

	private int leaveYearStartMonth;

	/** IANA zone id; null means the tenant's default timezone. */
	private String timezone;

	private String dateFormat;

	@Enumerated(EnumType.STRING)
	private DayOfWeek weekStartDay;

	private String organizationName;

	private String logoUrl;

}
