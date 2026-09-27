package com.bsolz.lms.organization.service;

import com.bsolz.lms.organization.entity.WorkSchedule;
import com.bsolz.lms.organization.exception.OrganizationErrorCode;
import com.bsolz.lms.organization.mapper.OrganizationMapper;
import com.bsolz.lms.organization.repository.WorkScheduleRepository;
import com.bsolz.lms.organization.web.dto.WorkScheduleRequest;
import com.bsolz.lms.organization.web.dto.WorkScheduleResponse;
import com.bsolz.lms.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Work schedules. Exactly one is the default once the tenant is provisioned (seeded Mon-Fri). */
@Service
@Transactional
@RequiredArgsConstructor
public class WorkScheduleService {

	private final WorkScheduleRepository workScheduleRepository;

	private final OrganizationMapper mapper;

	@Transactional(readOnly = true)
	public List<WorkScheduleResponse> list() {
		return workScheduleRepository.findAll(Sort.by("name")).stream().map(mapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public WorkScheduleResponse get(UUID id) {
		return mapper.toResponse(require(id));
	}

	public WorkScheduleResponse create(WorkScheduleRequest request) {
		if (workScheduleRepository.existsByNameIgnoreCase(request.name().trim())) {
			throw nameTaken(request.name());
		}
		WorkSchedule schedule = new WorkSchedule();
		apply(schedule, request);
		return mapper.toResponse(workScheduleRepository.save(schedule));
	}

	public WorkScheduleResponse update(UUID id, WorkScheduleRequest request) {
		WorkSchedule schedule = require(id);
		if (workScheduleRepository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
			throw nameTaken(request.name());
		}
		if (schedule.isDefaultSchedule() && !request.defaultSchedule()) {
			throw new ApiException(OrganizationErrorCode.DEFAULT_WORK_SCHEDULE_REQUIRED,
					"Make another schedule the default instead of unsetting this one");
		}
		apply(schedule, request);
		return mapper.toResponse(schedule);
	}

	WorkSchedule require(UUID id) {
		return workScheduleRepository.findById(id)
				.orElseThrow(() -> new ApiException(OrganizationErrorCode.WORK_SCHEDULE_NOT_FOUND,
						"Work schedule not found"));
	}

	private void apply(WorkSchedule schedule, WorkScheduleRequest request) {
		if (request.defaultSchedule() && !schedule.isDefaultSchedule()) {
			workScheduleRepository.findByDefaultScheduleTrue().ifPresent(current -> {
				current.setDefaultSchedule(false);
				workScheduleRepository.saveAndFlush(current); // free the single-default unique index first
			});
		}
		schedule.setName(request.name().trim());
		schedule.setWorkingDays(request.workingDays());
		schedule.setDefaultSchedule(request.defaultSchedule());
	}

	private static ApiException nameTaken(String name) {
		return new ApiException(OrganizationErrorCode.WORK_SCHEDULE_NAME_TAKEN,
				"A work schedule named '" + name + "' already exists");
	}

}
