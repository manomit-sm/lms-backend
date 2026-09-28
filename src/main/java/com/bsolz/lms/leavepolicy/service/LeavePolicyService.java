package com.bsolz.lms.leavepolicy.service;

import com.bsolz.lms.leavepolicy.api.LeavePolicyApi;
import com.bsolz.lms.leavepolicy.entity.ApplicabilityRule;
import com.bsolz.lms.leavepolicy.entity.LeavePolicy;
import com.bsolz.lms.leavepolicy.entity.LeaveType;
import com.bsolz.lms.leavepolicy.exception.LeavePolicyErrorCode;
import com.bsolz.lms.leavepolicy.mapper.LeavePolicyMapper;
import com.bsolz.lms.leavepolicy.repository.LeavePolicyRepository;
import com.bsolz.lms.leavepolicy.repository.LeaveTypeRepository;
import com.bsolz.lms.leavepolicy.web.dto.EffectivePolicyResponse;
import com.bsolz.lms.leavepolicy.web.dto.LeavePolicyRequest;
import com.bsolz.lms.leavepolicy.web.dto.LeavePolicyResponse;
import com.bsolz.lms.organization.api.EmployeeSummary;
import com.bsolz.lms.organization.api.EmployeeVisibility;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.organization.model.enums.OrgUnitType;
import com.bsolz.lms.settings.api.SettingsApi;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.exception.CommonErrorCode;
import com.bsolz.lms.shared.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leave policies. Two active policies of the same leave type may not share an applicability rule while
 * their effective dates overlap, so resolution never has to choose between equally specific policies.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class LeavePolicyService {

	private final LeavePolicyRepository repository;

	private final LeaveTypeRepository leaveTypeRepository;

	private final LeaveTypeService leaveTypeService;

	private final LeavePolicyApi policyApi;

	private final OrganizationApi organizationApi;

	private final EmployeeVisibility employeeVisibility;

	private final SettingsApi settings;

	private final LeavePolicyMapper mapper;

	@Transactional(readOnly = true)
	public List<LeavePolicyResponse> list(UUID leaveTypeId) {
		List<LeavePolicy> policies = leaveTypeId == null ? repository.findAllByOrderByNameAsc()
				: repository.findAllByLeaveTypeIdOrderByNameAsc(leaveTypeId);
		return policies.stream().map(mapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public LeavePolicyResponse get(UUID id) {
		return mapper.toResponse(require(id));
	}

	public LeavePolicyResponse create(LeavePolicyRequest request) {
		if (repository.existsByNameIgnoreCase(request.name().trim())) {
			throw nameTaken(request.name());
		}
		LeavePolicy policy = new LeavePolicy();
		policy.setLeaveType(leaveTypeService.require(request.leaveTypeId()));
		apply(policy, request);
		return mapper.toResponse(repository.save(policy));
	}

	public LeavePolicyResponse update(UUID id, LeavePolicyRequest request) {
		LeavePolicy policy = require(id);
		if (!policy.getLeaveType().getId().equals(request.leaveTypeId())) {
			throw new ApiException(LeavePolicyErrorCode.POLICY_LEAVE_TYPE_IMMUTABLE,
					"A policy's leave type can't change; create a new policy instead");
		}
		if (repository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
			throw nameTaken(request.name());
		}
		apply(policy, request);
		repository.flush();
		return mapper.toResponse(policy);
	}

	/**
	 * The policies governing an employee (the current user when null) on a date (today when null), for one
	 * leave type or every active one.
	 */
	@Transactional(readOnly = true)
	public List<EffectivePolicyResponse> effective(UUID employeeId, UUID leaveTypeId, LocalDate date) {
		UUID target = employeeId != null ? employeeId : currentEmployeeId();
		if (!employeeVisibility.canView(target)) {
			throw new AccessDeniedException("Employee not visible");
		}
		EmployeeSummary employee = organizationApi.findEmployee(target)
				.orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "Employee not found"));
		LocalDate asOf = date != null ? date : settings.today();
		List<LeaveType> types = leaveTypeId != null ? List.of(leaveTypeService.require(leaveTypeId))
				: leaveTypeRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc();
		return types.stream().map(type -> {
			var resolved = policyApi.resolve(employee, type.getId(), asOf).orElse(null);
			return new EffectivePolicyResponse(type.getId(), type.getCode(), type.getName(), resolved != null,
					resolved);
		}).toList();
	}

	private void apply(LeavePolicy policy, LeavePolicyRequest request) {
		if (request.effectiveFrom() != null && request.effectiveTo() != null
				&& request.effectiveTo().isBefore(request.effectiveFrom())) {
			throw new ApiException(LeavePolicyErrorCode.INVALID_EFFECTIVE_DATES,
					"effectiveTo can't be before effectiveFrom");
		}
		// A rule without criteria matches everyone, which makes the others irrelevant: store no rules.
		List<ApplicabilityRule> rules = request.appliesTo() == null ? List.of()
				: request.appliesTo().stream().map(mapper::toRule).distinct().toList();
		if (rules.stream().anyMatch(ApplicabilityRule::isEveryone)) {
			rules = List.of();
		}
		requireExisting(OrgUnitType.DEPARTMENT, rules, ApplicabilityRule::departmentId);
		requireExisting(OrgUnitType.DESIGNATION, rules, ApplicabilityRule::designationId);
		requireExisting(OrgUnitType.LOCATION, rules, ApplicabilityRule::locationId);

		policy.setName(request.name().trim());
		policy.setDescription(request.description());
		policy.setEffectiveFrom(request.effectiveFrom());
		policy.setEffectiveTo(request.effectiveTo());
		policy.setEntitlementDays(request.entitlementDays());
		policy.setAccrualMethod(request.accrualMethod());
		policy.setProrateOnJoining(request.prorateOnJoining());
		policy.setCarryForwardMaxDays(Objects.requireNonNullElse(request.carryForwardMaxDays(), BigDecimal.ZERO));
		policy.setCarryForwardExpiryMonths(request.carryForwardExpiryMonths());
		policy.setNegativeBalanceLimit(Objects.requireNonNullElse(request.negativeBalanceLimit(), BigDecimal.ZERO));
		policy.setMaxConsecutiveDays(request.maxConsecutiveDays());
		policy.setMinNoticeDays(Objects.requireNonNullElse(request.minNoticeDays(), 0));
		policy.setBackdatingAllowedDays(Objects.requireNonNullElse(request.backdatingAllowedDays(), 0));
		policy.setAttachmentRequiredAfterDays(request.attachmentRequiredAfterDays());
		policy.setAllowedDuringProbation(request.allowedDuringProbation() == null || request.allowedDuringProbation());
		policy.setSandwichRule(Boolean.TRUE.equals(request.sandwichRule()));
		policy.setActive(request.active() == null || request.active());
		policy.getAppliesTo().clear();
		policy.getAppliesTo().addAll(rules);
		if (policy.isActive()) {
			requireNoConflict(policy);
		}
	}

	private void requireNoConflict(LeavePolicy policy) {
		for (LeavePolicy other : repository.findAllByLeaveTypeIdOrderByNameAsc(policy.getLeaveType().getId())) {
			if (other.getId().equals(policy.getId()) || !other.isActive()
					|| !other.overlaps(policy.getEffectiveFrom(), policy.getEffectiveTo())) {
				continue;
			}
			if (other.getEffectiveRules().stream().anyMatch(policy.getEffectiveRules()::contains)) {
				throw new ApiException(LeavePolicyErrorCode.POLICY_CONFLICT, "Policy '" + other.getName()
						+ "' already applies to the same employees for this leave type during these dates");
			}
		}
	}

	private void requireExisting(OrgUnitType type, List<ApplicabilityRule> rules,
			Function<ApplicabilityRule, UUID> criterion) {
		Set<UUID> ids = rules.stream().map(criterion).filter(Objects::nonNull).collect(Collectors.toSet());
		Set<UUID> existing = organizationApi.findExistingUnitIds(type, ids);
		ids.removeAll(existing);
		if (!ids.isEmpty()) {
			throw new ApiException(LeavePolicyErrorCode.UNKNOWN_ORG_UNIT,
					"Unknown " + type.name().toLowerCase() + " id(s): " + ids);
		}
	}

	private LeavePolicy require(UUID id) {
		return repository.findWithRulesById(id)
				.orElseThrow(() -> new ApiException(LeavePolicyErrorCode.LEAVE_POLICY_NOT_FOUND, "Leave policy not found"));
	}

	private static UUID currentEmployeeId() {
		UUID employeeId = CurrentUser.require().employeeId();
		if (employeeId == null) {
			throw new ApiException(CommonErrorCode.NOT_FOUND, "Your user is not linked to an employee record");
		}
		return employeeId;
	}

	private static ApiException nameTaken(String name) {
		return new ApiException(LeavePolicyErrorCode.LEAVE_POLICY_NAME_TAKEN, "Policy '" + name + "' already exists");
	}

}
