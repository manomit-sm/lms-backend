package com.bsolz.lms.holiday.entity;

import com.bsolz.lms.holiday.model.enums.HolidayType;
import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A holiday. It applies to everyone unless it lists locations and/or departments; then an employee
 * must be in one of the listed locations and one of the listed departments.
 */
@Getter
@Setter
@Entity
@Table(name = "holiday")
public class Holiday extends BaseEntity {

	private String name;

	@Column(name = "holiday_date")
	private LocalDate date;

	@Enumerated(EnumType.STRING)
	private HolidayType type;

	private String description;

	@ElementCollection
	@CollectionTable(name = "holiday_location", joinColumns = @JoinColumn(name = "holiday_id"))
	@Column(name = "location_id")
	private Set<UUID> locationIds = new HashSet<>();

	@ElementCollection
	@CollectionTable(name = "holiday_department", joinColumns = @JoinColumn(name = "holiday_id"))
	@Column(name = "department_id")
	private Set<UUID> departmentIds = new HashSet<>();

	/** A null location or department only matches holidays not limited by it. */
	public boolean appliesTo(UUID locationId, UUID departmentId) {
		return appliesToLocation(locationId) && appliesToDepartment(departmentId);
	}

	public boolean appliesToLocation(UUID locationId) {
		return locationIds.isEmpty() || (locationId != null && locationIds.contains(locationId));
	}

	public boolean appliesToDepartment(UUID departmentId) {
		return departmentIds.isEmpty() || (departmentId != null && departmentIds.contains(departmentId));
	}

}
