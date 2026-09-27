package com.bsolz.lms.organization.entity;

import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/** Which days of the week are working days. Days not listed are weekends for leave calculation. */
@Getter
@Setter
@Entity
@Table(name = "work_schedule")
public class WorkSchedule extends BaseEntity {

	private String name;

	private boolean monday;

	private boolean tuesday;

	private boolean wednesday;

	private boolean thursday;

	private boolean friday;

	private boolean saturday;

	private boolean sunday;

	private boolean defaultSchedule;

	public Set<DayOfWeek> getWorkingDays() {
		Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
		if (monday) days.add(DayOfWeek.MONDAY);
		if (tuesday) days.add(DayOfWeek.TUESDAY);
		if (wednesday) days.add(DayOfWeek.WEDNESDAY);
		if (thursday) days.add(DayOfWeek.THURSDAY);
		if (friday) days.add(DayOfWeek.FRIDAY);
		if (saturday) days.add(DayOfWeek.SATURDAY);
		if (sunday) days.add(DayOfWeek.SUNDAY);
		return days;
	}

	public void setWorkingDays(Collection<DayOfWeek> days) {
		monday = days.contains(DayOfWeek.MONDAY);
		tuesday = days.contains(DayOfWeek.TUESDAY);
		wednesday = days.contains(DayOfWeek.WEDNESDAY);
		thursday = days.contains(DayOfWeek.THURSDAY);
		friday = days.contains(DayOfWeek.FRIDAY);
		saturday = days.contains(DayOfWeek.SATURDAY);
		sunday = days.contains(DayOfWeek.SUNDAY);
	}

}
