package com.bsolz.lms.settings.web;

import com.bsolz.lms.settings.service.SettingsService;
import com.bsolz.lms.settings.web.dto.SettingsRequest;
import com.bsolz.lms.settings.web.dto.SettingsResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Readable by every tenant user (the frontend needs the date format and timezone); changed with SETTINGS_MANAGE. */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
class SettingsController {

	private final SettingsService service;

	@GetMapping
	SettingsResponse get() {
		return service.get();
	}

	@GetMapping("/date-formats")
	List<String> dateFormats() {
		return SettingsService.DATE_FORMATS;
	}

	@PutMapping
	@PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
	SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
		return service.update(request);
	}

}
