package com.bsolz.lms.organization.entity;

import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "department")
public class Department extends BaseEntity {

	private String code;

	private String name;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_department_id")
	private Department parent;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "head_employee_id")
	private Employee head;

	private boolean active = true;

}
