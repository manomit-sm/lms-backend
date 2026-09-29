package com.bsolz.lms.holiday.service;

import com.bsolz.lms.holiday.api.HolidayApi;
import com.bsolz.lms.holiday.api.HolidayInfo;
import com.bsolz.lms.holiday.mapper.HolidayMapper;
import com.bsolz.lms.holiday.repository.HolidayRepository;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.OrganizationApi;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class HolidayApiService implements HolidayApi {

	private final HolidayRepository repository;

	private final OrganizationApi organizationApi;

	private final HolidayMapper mapper;

	@Override
	public List<HolidayInfo> findHolidaysFor(EmployeeSummary employee, LocalDate from, LocalDate to) {
		return repository.findAllByDateBetweenOrderByDateAscNameAsc(from, to).stream()
				.filter(holiday -> holiday.appliesTo(employee.locationId(), employee.departmentId()))
				.map(mapper::toInfo)
				.toList();
	}

	@Override
	public List<HolidayInfo> findHolidaysFor(UUID employeeId, LocalDate from, LocalDate to) {
		return organizationApi.findEmployee(employeeId).map(employee -> findHolidaysFor(employee, from, to))
				.orElse(List.of());
	}

}
