package com.bsolz.lms.organization.service;

import com.bsolz.lms.organization.entity.Department;
import com.bsolz.lms.organization.entity.Employee;
import com.bsolz.lms.organization.exception.OrganizationErrorCode;
import com.bsolz.lms.organization.mapper.OrganizationMapper;
import com.bsolz.lms.organization.repository.DepartmentRepository;
import com.bsolz.lms.organization.repository.EmployeeRepository;
import com.bsolz.lms.organization.web.dto.DepartmentRequest;
import com.bsolz.lms.organization.web.dto.DepartmentResponse;
import com.bsolz.lms.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class DepartmentService {

	private final DepartmentRepository departmentRepository;

	private final EmployeeRepository employeeRepository;

	private final OrganizationMapper mapper;

	@Transactional(readOnly = true)
	public List<DepartmentResponse> list() {
		return departmentRepository.findAll(Sort.by("name")).stream().map(mapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public DepartmentResponse get(UUID id) {
		return mapper.toResponse(require(id));
	}

	public DepartmentResponse create(DepartmentRequest request) {
		if (departmentRepository.existsByCodeIgnoreCase(request.code().trim())) {
			throw codeTaken(request.code());
		}
		Department department = new Department();
		apply(department, request);
		return mapper.toResponse(departmentRepository.save(department));
	}

	public DepartmentResponse update(UUID id, DepartmentRequest request) {
		Department department = require(id);
		if (departmentRepository.existsByCodeIgnoreCaseAndIdNot(request.code().trim(), id)) {
			throw codeTaken(request.code());
		}
		if (request.parentDepartmentId() != null && departmentRepository.isInSubtree(id, request.parentDepartmentId())) {
			throw new ApiException(OrganizationErrorCode.DEPARTMENT_CYCLE,
					"A department can't be moved under itself or one of its sub-departments");
		}
		apply(department, request);
		return mapper.toResponse(department);
	}

	Department require(UUID id) {
		return departmentRepository.findById(id)
				.orElseThrow(() -> new ApiException(OrganizationErrorCode.DEPARTMENT_NOT_FOUND, "Department not found"));
	}

	private void apply(Department department, DepartmentRequest request) {
		department.setCode(request.code().trim());
		department.setName(request.name().trim());
		department.setParent(request.parentDepartmentId() == null ? null : require(request.parentDepartmentId()));
		department.setHead(request.headEmployeeId() == null ? null : requireCurrentEmployee(request.headEmployeeId()));
		department.setActive(request.active() == null || request.active());
	}

	private Employee requireCurrentEmployee(UUID employeeId) {
		Employee employee = employeeRepository.findById(employeeId)
				.orElseThrow(() -> new ApiException(OrganizationErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found"));
		if (employee.isExited()) {
			throw new ApiException(OrganizationErrorCode.EMPLOYEE_EXITED, "Employee has exited");
		}
		return employee;
	}

	private static ApiException codeTaken(String code) {
		return new ApiException(OrganizationErrorCode.DEPARTMENT_CODE_TAKEN, "Department code '" + code + "' is taken");
	}

}
