package com.bsolz.lms.organization.service;

import com.bsolz.lms.organization.api.EmployeeCreated;
import com.bsolz.lms.organization.api.EmployeeExited;
import com.bsolz.lms.organization.entity.Employee;
import com.bsolz.lms.organization.exception.OrganizationErrorCode;
import com.bsolz.lms.organization.mapper.OrganizationMapper;
import com.bsolz.lms.organization.model.enums.EmploymentStatus;
import com.bsolz.lms.organization.repository.EmployeeRepository;
import com.bsolz.lms.organization.web.dto.EmployeeRequest;
import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.organization.web.dto.ExitEmployeeRequest;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.security.CurrentUser;
import com.bsolz.lms.shared.security.DataScope;
import com.bsolz.lms.shared.tenancy.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employees and the reporting hierarchy. Publishes {@link EmployeeCreated} / {@link EmployeeExited}
 * (delivered after commit via the Modulith event registry) so identity can invite / disable users.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeService {

	private final EmployeeRepository employeeRepository;

	private final DepartmentService departmentService;

	private final DesignationService designationService;

	private final LocationService locationService;

	private final WorkScheduleService workScheduleService;

	private final EmployeeAccess employeeAccess;

	private final OrganizationMapper mapper;

	private final ApplicationEventPublisher events;

	/** Employees visible to the current user (their data scope), filtered and paged. */
	@Transactional(readOnly = true)
	public Page<EmployeeResponse> list(EmployeeFilter filter, Pageable pageable) {
		UUID self = CurrentUser.require().employeeId();
		DataScope scope = employeeAccess.currentScope();
		Set<UUID> visible = new HashSet<>();
		if (scope == DataScope.TEAM) {
			visible.addAll(employeeRepository.findReportingLineIds(self));
			visible.add(self);
		}
		else if (scope == DataScope.SELF && self != null) {
			visible.add(self);
		}
		Specification<Employee> specification = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (scope != DataScope.TENANT) {
				predicates.add(visible.isEmpty() ? cb.disjunction() : root.get("id").in(visible));
			}
			if (filter.search() != null && !filter.search().isBlank()) {
				String pattern = "%" + filter.search().trim().toLowerCase(Locale.ROOT)
						.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
				predicates.add(cb.or(cb.like(cb.lower(root.get("firstName")), pattern, '\\'),
						cb.like(cb.lower(root.get("lastName")), pattern, '\\'),
						cb.like(cb.lower(root.get("email")), pattern, '\\'),
						cb.like(cb.lower(root.get("employeeCode")), pattern, '\\')));
			}
			if (filter.departmentId() != null) {
				predicates.add(cb.equal(root.get("department").get("id"), filter.departmentId()));
			}
			if (filter.reportingManagerId() != null) {
				predicates.add(cb.equal(root.get("reportingManager").get("id"), filter.reportingManagerId()));
			}
			if (filter.status() != null) {
				predicates.add(cb.equal(root.get("employmentStatus"), filter.status()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
		return employeeRepository.findAll(specification, pageable).map(mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public EmployeeResponse get(UUID id) {
		return mapper.toResponse(requireWithDetails(id));
	}

	/** The current user's own employee record. */
	@Transactional(readOnly = true)
	public EmployeeResponse me() {
		return get(currentEmployeeId());
	}

	/** The current user's direct reports, or their whole reporting line. */
	@Transactional(readOnly = true)
	public List<EmployeeResponse> myTeam(boolean includeIndirect) {
		UUID self = currentEmployeeId();
		List<Employee> team;
		if (includeIndirect) {
			List<UUID> reportingLine = employeeRepository.findReportingLineIds(self);
			team = reportingLine.isEmpty() ? List.of()
					: employeeRepository.findAll((root, query, cb) -> root.get("id").in(reportingLine),
							Sort.by("firstName", "lastName"));
		}
		else {
			team = employeeRepository.findAllByReportingManagerIdOrderByFirstNameAscLastNameAsc(self);
		}
		return team.stream().map(mapper::toResponse).toList();
	}

	public EmployeeResponse create(EmployeeRequest request) {
		String email = normalizeEmail(request.email());
		if (employeeRepository.existsByEmployeeCodeIgnoreCase(request.employeeCode().trim())) {
			throw codeTaken(request.employeeCode());
		}
		if (employeeRepository.existsByEmailIgnoreCase(email)) {
			throw emailTaken(email);
		}
		Employee employee = new Employee();
		apply(employee, request, email);
		employee = employeeRepository.save(employee);
		events.publishEvent(new EmployeeCreated(TenantContext.require().id(), employee.getId(), email));
		return mapper.toResponse(employee);
	}

	public EmployeeResponse update(UUID id, EmployeeRequest request) {
		Employee employee = requireWithDetails(id);
		if (employee.isExited()) {
			throw new ApiException(OrganizationErrorCode.EMPLOYEE_EXITED, "Exited employees can't be changed");
		}
		String email = normalizeEmail(request.email());
		if (employeeRepository.existsByEmployeeCodeIgnoreCaseAndIdNot(request.employeeCode().trim(), id)) {
			throw codeTaken(request.employeeCode());
		}
		if (employeeRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
			throw emailTaken(email);
		}
		UUID managerId = request.reportingManagerId();
		if (managerId != null && (managerId.equals(id) || employeeRepository.isInReportingLine(id, managerId))) {
			throw new ApiException(OrganizationErrorCode.REPORTING_CYCLE,
					"An employee can't report to themselves or to someone in their own reporting line");
		}
		apply(employee, request, email);
		return mapper.toResponse(employee);
	}

	/** Marks the employee as exited. Their reports must be reassigned first. */
	public EmployeeResponse exit(UUID id, ExitEmployeeRequest request) {
		Employee employee = requireWithDetails(id);
		if (employee.isExited()) {
			throw new ApiException(OrganizationErrorCode.EMPLOYEE_EXITED, "Employee has already exited");
		}
		if (request.exitDate().isBefore(employee.getJoiningDate())) {
			throw new ApiException(OrganizationErrorCode.INVALID_DATES, "Exit date can't be before the joining date");
		}
		if (employeeRepository.existsByReportingManagerIdAndEmploymentStatusNot(id, EmploymentStatus.EXITED)) {
			throw new ApiException(OrganizationErrorCode.EMPLOYEE_HAS_REPORTS,
					"Reassign this employee's direct reports before they exit");
		}
		employee.exit(request.exitDate());
		events.publishEvent(new EmployeeExited(TenantContext.require().id(), id, request.exitDate()));
		return mapper.toResponse(employee);
	}

	private void apply(Employee employee, EmployeeRequest request, String email) {
		if (request.employmentStatus() == EmploymentStatus.EXITED) {
			throw new ApiException(OrganizationErrorCode.INVALID_EMPLOYMENT_STATUS,
					"Use the exit operation to mark an employee as exited");
		}
		if (request.probationEndDate() != null && request.probationEndDate().isBefore(request.joiningDate())) {
			throw new ApiException(OrganizationErrorCode.INVALID_DATES,
					"Probation end date can't be before the joining date");
		}
		employee.setEmployeeCode(request.employeeCode().trim());
		employee.setFirstName(request.firstName().trim());
		employee.setLastName(request.lastName().trim());
		employee.setEmail(email);
		employee.setPhone(request.phone());
		employee.setGender(request.gender());
		employee.setDepartment(departmentService.require(request.departmentId()));
		employee.setDesignation(request.designationId() == null ? null
				: designationService.require(request.designationId()));
		employee.setLocation(request.locationId() == null ? null : locationService.require(request.locationId()));
		employee.setReportingManager(request.reportingManagerId() == null ? null
				: requireCurrentManager(request.reportingManagerId()));
		employee.setWorkSchedule(request.workScheduleId() == null ? null
				: workScheduleService.require(request.workScheduleId()));
		employee.setEmploymentType(request.employmentType());
		employee.setEmploymentStatus(request.employmentStatus());
		employee.setJoiningDate(request.joiningDate());
		employee.setProbationEndDate(request.probationEndDate());
	}

	private Employee requireCurrentManager(UUID managerId) {
		Employee manager = employeeRepository.findById(managerId)
				.orElseThrow(() -> new ApiException(OrganizationErrorCode.EMPLOYEE_NOT_FOUND,
						"Reporting manager not found"));
		if (manager.isExited()) {
			throw new ApiException(OrganizationErrorCode.EMPLOYEE_EXITED, "Reporting manager has exited");
		}
		return manager;
	}

	private Employee requireWithDetails(UUID id) {
		return employeeRepository.findWithDetailsById(id)
				.orElseThrow(() -> new ApiException(OrganizationErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found"));
	}

	private static UUID currentEmployeeId() {
		UUID employeeId = CurrentUser.require().employeeId();
		if (employeeId == null) {
			throw new ApiException(OrganizationErrorCode.EMPLOYEE_NOT_FOUND,
					"Your user is not linked to an employee record");
		}
		return employeeId;
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static ApiException codeTaken(String code) {
		return new ApiException(OrganizationErrorCode.EMPLOYEE_CODE_TAKEN, "Employee code '" + code + "' is taken");
	}

	private static ApiException emailTaken(String email) {
		return new ApiException(OrganizationErrorCode.EMPLOYEE_EMAIL_TAKEN,
				"An employee with email '" + email + "' already exists");
	}

}
