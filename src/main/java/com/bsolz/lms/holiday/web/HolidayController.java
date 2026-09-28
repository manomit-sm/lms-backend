package com.bsolz.lms.holiday.web;

import com.bsolz.lms.holiday.exception.HolidayErrorCode;
import com.bsolz.lms.holiday.service.HolidayImportService;
import com.bsolz.lms.holiday.service.HolidayService;
import com.bsolz.lms.holiday.web.dto.HolidayImportResult;
import com.bsolz.lms.holiday.web.dto.HolidayRequest;
import com.bsolz.lms.holiday.web.dto.HolidayResponse;
import com.bsolz.lms.shared.exception.ApiException;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Holidays: readable by every tenant user, changed with HOLIDAY_MANAGE. Lists take a {@code year}
 * (default: the current one) or a {@code from}/{@code to} range.
 */
@RestController
@RequestMapping("/api/v1/holidays")
@RequiredArgsConstructor
class HolidayController {

	private final HolidayService service;

	private final HolidayImportService importService;

	@GetMapping
	List<HolidayResponse> list(@RequestParam(required = false) Integer year,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) UUID locationId, @RequestParam(required = false) UUID departmentId) {
		return service.list(service.range(year, from, to), locationId, departmentId);
	}

	/** Holidays that apply to the current user (by their location and department). */
	@GetMapping("/me")
	List<HolidayResponse> mine(@RequestParam(required = false) Integer year,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
		return service.mine(service.range(year, from, to));
	}

	@GetMapping("/{holidayId}")
	HolidayResponse get(@PathVariable UUID holidayId) {
		return service.get(holidayId);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('HOLIDAY_MANAGE')")
	ResponseEntity<HolidayResponse> create(@Valid @RequestBody HolidayRequest request) {
		HolidayResponse created = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/holidays/" + created.id())).body(created);
	}

	@PutMapping("/{holidayId}")
	@PreAuthorize("hasAuthority('HOLIDAY_MANAGE')")
	HolidayResponse update(@PathVariable UUID holidayId, @Valid @RequestBody HolidayRequest request) {
		return service.update(holidayId, request);
	}

	@DeleteMapping("/{holidayId}")
	@PreAuthorize("hasAuthority('HOLIDAY_MANAGE')")
	ResponseEntity<Void> delete(@PathVariable UUID holidayId) {
		service.delete(holidayId);
		return ResponseEntity.noContent().build();
	}

	/** CSV upload (multipart field {@code file}); see {@link HolidayImportService} for the format. */
	@PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('HOLIDAY_MANAGE')")
	HolidayImportResult importCsv(@RequestPart("file") MultipartFile file) throws IOException {
		if (file.isEmpty()) {
			throw new ApiException(HolidayErrorCode.INVALID_HOLIDAY_IMPORT, "The file is empty");
		}
		try (InputStream csv = file.getInputStream()) {
			return importService.importCsv(csv);
		}
	}

}
