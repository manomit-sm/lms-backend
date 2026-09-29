package com.bsolz.lms.leavepolicy.service;

import com.bsolz.lms.leavepolicy.entity.LeaveType;
import com.bsolz.lms.leavepolicy.exception.LeavePolicyErrorCode;
import com.bsolz.lms.leavepolicy.mapper.LeavePolicyMapper;
import com.bsolz.lms.leavepolicy.repository.LeaveTypeRepository;
import com.bsolz.lms.leavepolicy.web.dto.LeaveTypeRequest;
import com.bsolz.lms.leavepolicy.web.dto.LeaveTypeResponse;
import com.bsolz.lms.shared.exception.ApiException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class LeaveTypeService {

	private final LeaveTypeRepository repository;

	private final LeavePolicyMapper mapper;

	@Transactional(readOnly = true)
	public List<LeaveTypeResponse> list(boolean includeInactive) {
		List<LeaveType> types = includeInactive ? repository.findAllByOrderBySortOrderAscNameAsc()
				: repository.findAllByActiveTrueOrderBySortOrderAscNameAsc();
		return types.stream().map(mapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public LeaveTypeResponse get(UUID id) {
		return mapper.toResponse(require(id));
	}

	public LeaveTypeResponse create(LeaveTypeRequest request) {
		String code = request.code().toUpperCase(Locale.ROOT);
		if (repository.existsByCode(code)) {
			throw codeTaken(code);
		}
		if (repository.existsByNameIgnoreCase(request.name().trim())) {
			throw nameTaken(request.name());
		}
		LeaveType leaveType = new LeaveType();
		apply(leaveType, request, code);
		return mapper.toResponse(repository.save(leaveType));
	}

	public LeaveTypeResponse update(UUID id, LeaveTypeRequest request) {
		LeaveType leaveType = require(id);
		String code = request.code().toUpperCase(Locale.ROOT);
		if (repository.existsByCodeAndIdNot(code, id)) {
			throw codeTaken(code);
		}
		if (repository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
			throw nameTaken(request.name());
		}
		apply(leaveType, request, code);
		repository.flush();
		return mapper.toResponse(leaveType);
	}

	LeaveType require(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new ApiException(LeavePolicyErrorCode.LEAVE_TYPE_NOT_FOUND, "Leave type not found"));
	}

	private static void apply(LeaveType leaveType, LeaveTypeRequest request, String code) {
		leaveType.setCode(code);
		leaveType.setName(request.name().trim());
		leaveType.setDescription(request.description());
		leaveType.setColor(request.color().toUpperCase(Locale.ROOT));
		leaveType.setPaid(request.paid());
		leaveType.setBalanceTracked(request.balanceTracked());
		leaveType.setTimeOff(request.timeOff() == null || request.timeOff());
		leaveType.setHalfDayAllowed(request.halfDayAllowed() == null || request.halfDayAllowed());
		leaveType.setActive(request.active() == null || request.active());
		leaveType.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
	}

	private static ApiException codeTaken(String code) {
		return new ApiException(LeavePolicyErrorCode.LEAVE_TYPE_CODE_TAKEN, "Leave type code '" + code + "' is taken");
	}

	private static ApiException nameTaken(String name) {
		return new ApiException(LeavePolicyErrorCode.LEAVE_TYPE_NAME_TAKEN, "Leave type '" + name + "' already exists");
	}

}
