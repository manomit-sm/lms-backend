package com.bsolz.lms.leavepolicy.service;

import com.bsolz.lms.leavepolicy.entity.LeavePeriod;
import com.bsolz.lms.leavepolicy.exception.LeavePolicyErrorCode;
import com.bsolz.lms.leavepolicy.mapper.LeavePolicyMapper;
import com.bsolz.lms.leavepolicy.repository.LeavePeriodRepository;
import com.bsolz.lms.leavepolicy.web.dto.LeavePeriodRequest;
import com.bsolz.lms.leavepolicy.web.dto.LeavePeriodResponse;
import com.bsolz.lms.settings.api.SettingsApi;
import com.bsolz.lms.shared.exception.ApiException;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Leave periods: whole months, at most twelve, never overlapping (also enforced by an exclusion constraint). */
@Service
@Transactional
@RequiredArgsConstructor
public class LeavePeriodService {

	private final LeavePeriodRepository repository;

	private final SettingsApi settings;

	private final LeavePolicyMapper mapper;

	@Transactional(readOnly = true)
	public List<LeavePeriodResponse> list() {
		return repository.findAllByOrderByStartDateDesc().stream().map(mapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public LeavePeriodResponse get(UUID id) {
		return mapper.toResponse(repository.findById(id).orElseThrow(LeavePeriodService::notFound));
	}

	/** The period containing today (in the tenant's timezone). */
	@Transactional(readOnly = true)
	public LeavePeriodResponse current() {
		return mapper.toResponse(repository.findContaining(settings.today())
				.orElseThrow(() -> new ApiException(LeavePolicyErrorCode.LEAVE_PERIOD_NOT_FOUND,
						"No leave period covers today")));
	}

	public LeavePeriodResponse create(LeavePeriodRequest request) {
		LocalDate start = request.startDate();
		LocalDate end = request.endDate();
		if (start == null && end == null) {
			start = repository.findFirstByOrderByEndDateDesc()
					.map(latest -> latest.getEndDate().plusDays(1))
					.orElseGet(this::currentLeaveYearStart);
			end = start.plusYears(1).minusDays(1);
		}
		else if (start == null || end == null) {
			throw invalid("Give both startDate and endDate, or neither for the next leave year");
		}
		if (start.getDayOfMonth() != 1 || !end.equals(end.with(TemporalAdjusters.lastDayOfMonth()))) {
			throw invalid("A leave period starts on the first of a month and ends on the last day of a month");
		}
		if (!end.isAfter(start) || end.isAfter(start.plusYears(1).minusDays(1))) {
			throw invalid("A leave period lasts between one and twelve months");
		}
		if (repository.existsOverlapping(start, end)) {
			throw new ApiException(LeavePolicyErrorCode.LEAVE_PERIOD_OVERLAP,
					"The period " + start + " to " + end + " overlaps an existing leave period");
		}
		String name = request.name() != null && !request.name().isBlank() ? request.name().trim()
				: defaultName(start, end);
		if (repository.existsByNameIgnoreCase(name)) {
			throw new ApiException(LeavePolicyErrorCode.LEAVE_PERIOD_NAME_TAKEN,
					"A leave period named '" + name + "' already exists");
		}
		LeavePeriod period = new LeavePeriod();
		period.setName(name);
		period.setStartDate(start);
		period.setEndDate(end);
		return mapper.toResponse(repository.save(period));
	}

	/**
	 * The period containing the date, created if missing: the leave year containing it, shortened to fit
	 * between the neighbouring periods. Used by the rollover job, so it never fails on a name clash.
	 */
	LeavePeriod openContaining(LocalDate date) {
		Optional<LeavePeriod> existing = repository.findContaining(date);
		if (existing.isPresent()) {
			return existing.get();
		}
		LocalDate start = leaveYearStart(date);
		LocalDate end = start.plusYears(1).minusDays(1);
		Optional<LeavePeriod> previous = repository.findFirstByEndDateBeforeOrderByEndDateDesc(date);
		if (previous.isPresent() && !previous.get().getEndDate().isBefore(start)) {
			start = previous.get().getEndDate().plusDays(1);
		}
		Optional<LeavePeriod> next = repository.findFirstByStartDateAfterOrderByStartDateAsc(date);
		if (next.isPresent() && !next.get().getStartDate().isAfter(end)) {
			end = next.get().getStartDate().minusDays(1);
		}
		String name = defaultName(start, end);
		if (repository.existsByNameIgnoreCase(name)) {
			name = start + " to " + end;
		}
		LeavePeriod period = new LeavePeriod();
		period.setName(name);
		period.setStartDate(start);
		period.setEndDate(end);
		return repository.saveAndFlush(period);
	}

	private LocalDate currentLeaveYearStart() {
		return leaveYearStart(settings.today());
	}

	private LocalDate leaveYearStart(LocalDate date) {
		LocalDate start = date.withMonth(settings.current().leaveYearStartMonth()).withDayOfMonth(1);
		return start.isAfter(date) ? start.minusYears(1) : start;
	}

	private static String defaultName(LocalDate start, LocalDate end) {
		return start.getYear() == end.getYear() ? String.valueOf(start.getYear())
				: start.getYear() + "-" + String.format("%02d", end.getYear() % 100);
	}

	private static ApiException invalid(String message) {
		return new ApiException(LeavePolicyErrorCode.INVALID_LEAVE_PERIOD, message);
	}

	private static ApiException notFound() {
		return new ApiException(LeavePolicyErrorCode.LEAVE_PERIOD_NOT_FOUND, "Leave period not found");
	}

}
