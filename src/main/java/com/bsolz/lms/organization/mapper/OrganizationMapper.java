package com.bsolz.lms.organization.mapper;

import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.entity.Department;
import com.bsolz.lms.organization.entity.Designation;
import com.bsolz.lms.organization.entity.Employee;
import com.bsolz.lms.organization.entity.Location;
import com.bsolz.lms.organization.entity.WorkSchedule;
import com.bsolz.lms.organization.web.dto.DepartmentResponse;
import com.bsolz.lms.organization.web.dto.DesignationResponse;
import com.bsolz.lms.organization.web.dto.EmployeeResponse;
import com.bsolz.lms.organization.web.dto.LocationResponse;
import com.bsolz.lms.organization.web.dto.Reference;
import com.bsolz.lms.organization.web.dto.WorkScheduleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Entity to response mapping. Call inside a transaction: related entities are lazy. */
@Mapper
public interface OrganizationMapper {

	WorkScheduleResponse toResponse(WorkSchedule workSchedule);

	@Mapping(target = "workScheduleId", source = "workSchedule.id")
	LocationResponse toResponse(Location location);

	@Mapping(target = "parentDepartmentId", source = "parent.id")
	DepartmentResponse toResponse(Department department);

	DesignationResponse toResponse(Designation designation);

	@Mapping(target = "workScheduleId", source = "workSchedule.id")
	EmployeeResponse toResponse(Employee employee);

	@Mapping(target = "departmentId", source = "department.id")
	@Mapping(target = "designationId", source = "designation.id")
	@Mapping(target = "locationId", source = "location.id")
	@Mapping(target = "reportingManagerId", source = "reportingManager.id")
	@Mapping(target = "workScheduleId", source = "workSchedule.id")
	EmployeeSummary toSummary(Employee employee);

	default Reference toReference(Department department) {
		return department == null ? null : new Reference(department.getId(), department.getName());
	}

	default Reference toReference(Designation designation) {
		return designation == null ? null : new Reference(designation.getId(), designation.getName());
	}

	default Reference toReference(Location location) {
		return location == null ? null : new Reference(location.getId(), location.getName());
	}

	default Reference toReference(Employee employee) {
		return employee == null ? null : new Reference(employee.getId(), employee.getFullName());
	}

}
