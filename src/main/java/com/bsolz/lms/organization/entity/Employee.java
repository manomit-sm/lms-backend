package com.bsolz.lms.organization.entity;

import com.bsolz.lms.organization.model.enums.EmploymentStatus;
import com.bsolz.lms.organization.model.enums.EmploymentType;
import com.bsolz.lms.organization.model.enums.Gender;
import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "employee")
public class Employee extends BaseEntity {

	private String employeeCode;

	private String firstName;

	private String lastName;

	/** Stored lower-case; unique case-insensitively. */
	private String email;

	private String phone;

	@Enumerated(EnumType.STRING)
	private Gender gender;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Department department;

	@ManyToOne(fetch = FetchType.LAZY)
	private Designation designation;

	@ManyToOne(fetch = FetchType.LAZY)
	private Location location;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reporting_manager_id")
	private Employee reportingManager;

	/** Personal override; otherwise the location's, otherwise the tenant default. */
	@ManyToOne(fetch = FetchType.LAZY)
	private WorkSchedule workSchedule;

	@Enumerated(EnumType.STRING)
	private EmploymentType employmentType;

	@Enumerated(EnumType.STRING)
	private EmploymentStatus employmentStatus;

	private LocalDate joiningDate;

	private LocalDate probationEndDate;

	private LocalDate exitDate;

	public String getFullName() {
		return firstName + " " + lastName;
	}

	public boolean isExited() {
		return employmentStatus == EmploymentStatus.EXITED;
	}

	public void exit(LocalDate date) {
		employmentStatus = EmploymentStatus.EXITED;
		exitDate = date;
	}

}
