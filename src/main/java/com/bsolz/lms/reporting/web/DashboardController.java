package com.bsolz.lms.reporting.web;

import com.bsolz.lms.reporting.service.DashboardService;
import com.bsolz.lms.reporting.web.dto.DashboardSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The home screen: any tenant user; the sections depend on who they are. */
@RestController
@RequiredArgsConstructor
class DashboardController {

	private final DashboardService service;

	@GetMapping("/api/v1/dashboard/summary")
	DashboardSummaryResponse summary() {
		return service.summary();
	}

}
