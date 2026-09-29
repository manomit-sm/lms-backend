package com.bsolz.lms.calendar.web;

import com.bsolz.lms.calendar.model.enums.CalendarScope;
import com.bsolz.lms.calendar.service.CalendarService;
import com.bsolz.lms.calendar.web.dto.AvailabilityResponse;
import com.bsolz.lms.calendar.web.dto.CalendarResponse;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Calendars for any employee user. {@code scope} is {@code me}, {@code team} or {@code department};
 * {@code departmentId} picks another department than the caller's own, for callers who may see every
 * employee.
 */
@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
class CalendarController {

	private final CalendarService service;

	/** Leave and holidays from {@code from} to {@code to} (inclusive, at most 92 days). */
	@GetMapping
	CalendarResponse calendar(@RequestParam(defaultValue = "me") String scope,
			@RequestParam(required = false) UUID departmentId, @RequestParam LocalDate from,
			@RequestParam LocalDate to) {
		return service.calendar(CalendarScope.parse(scope), departmentId, from, to);
	}

	/** Who is available on {@code date}, and why not. */
	@GetMapping("/availability")
	AvailabilityResponse availability(@RequestParam LocalDate date,
			@RequestParam(defaultValue = "team") String scope, @RequestParam(required = false) UUID departmentId) {
		return service.availability(CalendarScope.parse(scope), departmentId, date);
	}

}
