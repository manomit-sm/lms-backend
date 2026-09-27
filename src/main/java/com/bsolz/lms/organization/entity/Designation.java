package com.bsolz.lms.organization.entity;

import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "designation")
public class Designation extends BaseEntity {

	private String name;

	/** Optional seniority level, for ordering. */
	private Integer level;

	private boolean active = true;

}
