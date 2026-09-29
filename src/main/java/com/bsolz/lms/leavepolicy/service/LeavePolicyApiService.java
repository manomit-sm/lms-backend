package com.bsolz.lms.leavepolicy.service;

import com.bsolz.lms.leavepolicy.api.LeavePeriodInfo;
import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.leavepolicy.api.LeaveTypeInfo;
import com.bsolz.lms.leavepolicy.api.ResolvedPolicy;
import com.bsolz.lms.leavepolicy.domain.PolicyMatcher;
import com.bsolz.lms.leavepolicy.entity.LeavePolicy;
import com.bsolz.lms.leavepolicy.entity.LeaveType;
import com.bsolz.lms.leavepolicy.mapper.LeavePolicyMapper;
import com.bsolz.lms.leavepolicy.model.enums.LeavePeriodStatus;
import com.bsolz.lms.leavepolicy.repository.LeavePeriodRepository;
import com.bsolz.lms.leavepolicy.repository.LeavePolicyRepository;
import com.bsolz.lms.leavepolicy.repository.LeaveTypeRepository;
import com.bsolz.lms.organization.api.EmployeeSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class LeavePolicyApiService implements LeavePolicyApi {

	private final LeaveTypeRepository leaveTypeRepository;

	private final LeavePeriodRepository periodRepository;

	private final LeavePolicyRepository policyRepository;

	private final LeavePeriodService periodService;

	private final LeavePolicyMapper mapper;

	@Override
	public Optional<LeaveTypeInfo> findLeaveType(UUID leaveTypeId) {
		return leaveTypeRepository.findById(leaveTypeId).map(mapper::toInfo);
	}

	@Override
	public List<LeaveTypeInfo> findActiveLeaveTypes() {
		return leaveTypeRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream().map(mapper::toInfo).toList();
	}

	@Override
	public Optional<LeavePeriodInfo> findPeriod(UUID leavePeriodId) {
		return periodRepository.findById(leavePeriodId).map(mapper::toInfo);
	}

	@Override
	public Optional<LeavePeriodInfo> findPeriodContaining(LocalDate date) {
		return periodRepository.findContaining(date).map(mapper::toInfo);
	}

	@Override
	public List<LeavePeriodInfo> findOpenPeriods() {
		return periodRepository.findAllByStatusOrderByStartDateAsc(LeavePeriodStatus.OPEN).stream()
				.map(mapper::toInfo)
				.toList();
	}

	@Override
	@Transactional
	public LeavePeriodInfo openPeriodContaining(LocalDate date) {
		return mapper.toInfo(periodService.openContaining(date));
	}

	@Override
	@Transactional
	public void closePeriod(UUID leavePeriodId) {
		periodRepository.findById(leavePeriodId).orElseThrow(() -> new IllegalArgumentException(
				"Unknown leave period " + leavePeriodId)).setStatus(LeavePeriodStatus.CLOSED);
	}

	@Override
	public Optional<ResolvedPolicy> resolve(EmployeeSummary employee, UUID leaveTypeId, LocalDate asOf) {
		if (!leaveTypeRepository.findById(leaveTypeId).map(LeaveType::isActive).orElse(false)) {
			return Optional.empty();
		}
		LeavePolicy best = null;
		int bestScore = -1;
		// Ordered by name, and only a strictly better match replaces the current one: deterministic.
		for (LeavePolicy policy : policyRepository.findAllByLeaveTypeIdOrderByNameAsc(leaveTypeId)) {
			if (!policy.isActive() || !policy.isInEffectOn(asOf)) {
				continue;
			}
			OptionalInt score = PolicyMatcher.bestMatch(policy.getEffectiveRules(), employee);
			if (score.isPresent() && score.getAsInt() > bestScore) {
				best = policy;
				bestScore = score.getAsInt();
			}
		}
		return Optional.ofNullable(best).map(mapper::toResolved);
	}

}
