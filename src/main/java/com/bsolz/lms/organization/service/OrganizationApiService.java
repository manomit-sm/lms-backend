package com.bsolz.lms.organization.service;

import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.organization.mapper.OrganizationMapper;
import com.bsolz.lms.organization.repository.EmployeeRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class OrganizationApiService implements OrganizationApi {

	private final EmployeeRepository employeeRepository;

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

}
