package com.bsolz.lms.organization.service;

import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.organization.entity.Location;
import com.bsolz.lms.organization.entity.WorkSchedule;
import com.bsolz.lms.organization.mapper.OrganizationMapper;
import com.bsolz.lms.organization.model.enums.EmploymentStatus;
import com.bsolz.lms.organization.model.enums.OrgUnitType;
import com.bsolz.lms.organization.repository.DepartmentRepository;
import com.bsolz.lms.organization.repository.DesignationRepository;
import com.bsolz.lms.organization.repository.EmployeeRepository;
import com.bsolz.lms.organization.repository.LocationRepository;
import com.bsolz.lms.organization.repository.WorkScheduleRepository;
import com.bsolz.lms.shared.entity.BaseEntity;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class OrganizationApiService implements OrganizationApi {

	private final EmployeeRepository employeeRepository;

	private final DepartmentRepository departmentRepository;

	private final LocationRepository locationRepository;

	private final DesignationRepository designationRepository;

	private final WorkScheduleRepository workScheduleRepository;

	private final OrganizationMapper mapper;

	@Override
	public Optional<EmployeeSummary> findEmployee(UUID employeeId) {
		return employeeRepository.findById(employeeId).map(mapper::toSummary);
	}

	@Override
	public List<EmployeeSummary> findEmployees(Collection<UUID> employeeIds) {
		return employeeRepository.findAllById(employeeIds).stream().map(mapper::toSummary).toList();
	}

	@Override
	public List<EmployeeSummary> findCurrentEmployees() {
		return employeeRepository.findAllByEmploymentStatusNot(EmploymentStatus.EXITED).stream()
				.map(mapper::toSummary)
				.toList();
	}

	@Override
	public List<EmployeeSummary> findCurrentEmployeesInDepartment(UUID departmentId) {
		return employeeRepository.findAllByDepartmentIdAndEmploymentStatusNot(departmentId, EmploymentStatus.EXITED)
				.stream().map(mapper::toSummary).toList();
	}

	@Override
	public List<EmployeeSummary> findCurrentDirectReports(UUID managerId) {
		return employeeRepository.findAllByReportingManagerIdAndEmploymentStatusNot(managerId, EmploymentStatus.EXITED)
				.stream().map(mapper::toSummary).toList();
	}

	@Override
	public Map<UUID, ZoneId> findLocationTimezones() {
		return locationRepository.findAll().stream()
				.collect(Collectors.toMap(Location::getId, location -> ZoneId.of(location.getTimezone())));
	}

	@Override
	public Set<UUID> findReportingLine(UUID managerId) {
		return new LinkedHashSet<>(employeeRepository.findReportingLineIds(managerId));
	}

	@Override
	public List<UUID> findManagerChain(UUID employeeId) {
		return employeeRepository.findManagerChainIds(employeeId);
	}

	@Override
	public boolean isInReportingLine(UUID managerId, UUID employeeId) {
		return employeeRepository.isInReportingLine(managerId, employeeId);
	}

	@Override
	public Set<DayOfWeek> findWorkingDays(UUID employeeId) {
		return employeeRepository.findById(employeeId).map(employee -> {
			WorkSchedule schedule = employee.getWorkSchedule();
			if (schedule == null && employee.getLocation() != null) {
				schedule = employee.getLocation().getWorkSchedule();
			}
			if (schedule == null) {
				schedule = workScheduleRepository.findByDefaultScheduleTrue()
						.orElseThrow(() -> new IllegalStateException("Tenant has no default work schedule"));
			}
			return schedule.getWorkingDays();
		}).orElse(Set.of());
	}

	@Override
	public Set<UUID> findExistingUnitIds(OrgUnitType type, Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Set.of();
		}
		List<? extends BaseEntity> found = switch (type) {
			case DEPARTMENT -> departmentRepository.findAllById(ids);
			case LOCATION -> locationRepository.findAllById(ids);
			case DESIGNATION -> designationRepository.findAllById(ids);
		};
		return found.stream().map(BaseEntity::getId).collect(Collectors.toSet());
	}

	@Override
	public Map<String, UUID> findUnitIdsByCode(OrgUnitType type, Collection<String> codes) {
		if (codes.isEmpty()) {
			return Map.of();
		}
		Set<String> lowerCodes = codes.stream().map(code -> code.trim().toLowerCase(Locale.ROOT))
				.collect(Collectors.toSet());
		Map<String, UUID> ids = new HashMap<>();
		switch (type) {
			case DEPARTMENT -> departmentRepository.findAllByLowerCodeIn(lowerCodes)
					.forEach(department -> ids.put(department.getCode().toUpperCase(Locale.ROOT), department.getId()));
			case LOCATION -> locationRepository.findAllByLowerCodeIn(lowerCodes)
					.forEach(location -> ids.put(location.getCode().toUpperCase(Locale.ROOT), location.getId()));
			case DESIGNATION -> throw new IllegalArgumentException("Designations have no code");
		}
		return ids;
	}

}
