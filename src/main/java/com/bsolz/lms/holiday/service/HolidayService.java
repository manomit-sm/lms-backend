package com.bsolz.lms.holiday.service;

import com.bsolz.lms.holiday.entity.Holiday;
import com.bsolz.lms.holiday.exception.HolidayErrorCode;
import com.bsolz.lms.holiday.mapper.HolidayMapper;
import com.bsolz.lms.holiday.model.enums.HolidayType;
import com.bsolz.lms.holiday.repository.HolidayRepository;
import com.bsolz.lms.holiday.web.dto.HolidayRequest;
import com.bsolz.lms.holiday.web.dto.HolidayResponse;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.organization.model.enums.OrgUnitType;
import com.bsolz.lms.settings.api.SettingsApi;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.exception.CommonErrorCode;
import com.bsolz.lms.shared.security.CurrentUser;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class HolidayService {

	/** Longest range a single list request may cover. */
	static final int MAX_RANGE_DAYS = 731;

	private final HolidayRepository repository;

	private final OrganizationApi organizationApi;

	private final SettingsApi settings;

	private final HolidayMapper mapper;

	/** Holidays in a date range (or year), optionally only those applying to a location and/or department. */
	@Transactional(readOnly = true)
	public List<HolidayResponse> list(DateRange range, UUID locationId, UUID departmentId) {
		return repository.findAllByDateBetweenOrderByDateAscNameAsc(range.from(), range.to()).stream()
				.filter(holiday -> locationId == null || holiday.appliesToLocation(locationId))
				.filter(holiday -> departmentId == null || holiday.appliesToDepartment(departmentId))
				.map(mapper::toResponse)
				.toList();
	}

	/** Holidays applying to the current user's employee record. */
	@Transactional(readOnly = true)
	public List<HolidayResponse> mine(DateRange range) {
		UUID employeeId = CurrentUser.require().employeeId();
		EmployeeSummary employee = employeeId == null ? null : organizationApi.findEmployee(employeeId).orElse(null);
		if (employee == null) {
			throw new ApiException(CommonErrorCode.NOT_FOUND, "Your user is not linked to an employee record");
		}
		return repository.findAllByDateBetweenOrderByDateAscNameAsc(range.from(), range.to()).stream()
				.filter(holiday -> holiday.appliesTo(employee.locationId(), employee.departmentId()))
				.map(mapper::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public HolidayResponse get(UUID id) {
		return mapper.toResponse(require(id));
	}

	public HolidayResponse create(HolidayRequest request) {
		if (repository.existsByDateAndNameIgnoreCase(request.date(), request.name().trim())) {
			throw alreadyExists(request);
		}
		Holiday holiday = new Holiday();
		apply(holiday, request);
		return mapper.toResponse(repository.save(holiday));
	}

	public HolidayResponse update(UUID id, HolidayRequest request) {
		Holiday holiday = require(id);
		if (repository.existsByDateAndNameIgnoreCaseAndIdNot(request.date(), request.name().trim(), id)) {
			throw alreadyExists(request);
		}
		apply(holiday, request);
		repository.flush();
		return mapper.toResponse(holiday);
	}

	public void delete(UUID id) {
		repository.delete(require(id));
	}

	/** {@code from}/{@code to} together, or a year (the current one when nothing is given). */
	@Transactional(readOnly = true)
	public DateRange range(Integer year, LocalDate from, LocalDate to) {
		if (from == null && to == null) {
			int y = year != null ? year : settings.today().getYear();
			return new DateRange(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
		}
		if (from == null || to == null || year != null) {
			throw invalidRange("Give either a year, or both from and to");
		}
		if (to.isBefore(from) || to.isAfter(from.plusDays(MAX_RANGE_DAYS))) {
			throw invalidRange("to must be on or after from, and at most two years later");
		}
		return new DateRange(from, to);
	}

	void requireExistingUnits(OrgUnitType type, Set<UUID> ids) {
		Set<UUID> unknown = new HashSet<>(ids);
		unknown.removeAll(organizationApi.findExistingUnitIds(type, ids));
		if (!unknown.isEmpty()) {
			throw new ApiException(HolidayErrorCode.UNKNOWN_ORG_UNIT,
					"Unknown " + type.name().toLowerCase() + " id(s): " + unknown);
		}
	}

	private void apply(Holiday holiday, HolidayRequest request) {
		Set<UUID> locationIds = request.locationIds() == null ? Set.of() : request.locationIds();
		Set<UUID> departmentIds = request.departmentIds() == null ? Set.of() : request.departmentIds();
		requireExistingUnits(OrgUnitType.LOCATION, locationIds);
		requireExistingUnits(OrgUnitType.DEPARTMENT, departmentIds);
		holiday.setName(request.name().trim());
		holiday.setDate(request.date());
		holiday.setType(request.type() == null ? HolidayType.PUBLIC : request.type());
		holiday.setDescription(request.description());
		holiday.getLocationIds().clear();
		holiday.getLocationIds().addAll(locationIds);
		holiday.getDepartmentIds().clear();
		holiday.getDepartmentIds().addAll(departmentIds);
	}

	private Holiday require(UUID id) {
		return repository.findWithScopeById(id)
				.orElseThrow(() -> new ApiException(HolidayErrorCode.HOLIDAY_NOT_FOUND, "Holiday not found"));
	}

	private static ApiException alreadyExists(HolidayRequest request) {
		return new ApiException(HolidayErrorCode.HOLIDAY_ALREADY_EXISTS,
				"'" + request.name().trim() + "' on " + request.date() + " already exists");
	}

	private static ApiException invalidRange(String message) {
		return new ApiException(HolidayErrorCode.INVALID_DATE_RANGE, message);
	}

	/** Inclusive. */
	public record DateRange(LocalDate from, LocalDate to) {
	}

}
