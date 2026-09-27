package com.bsolz.lms.organization.entity;

import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "location")
public class Location extends BaseEntity {

	private String code;

	private String name;

	/** ISO 3166-1 alpha-2. */
	private String countryCode;

	/** IANA zone id. */
	private String timezone;

	@ManyToOne(fetch = FetchType.LAZY)
	private WorkSchedule workSchedule;

	private boolean active = true;

}
