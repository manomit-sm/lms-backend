package com.bsolz.lms.audit.web;

import com.bsolz.lms.audit.model.enums.AuditAction;
import com.bsolz.lms.audit.model.enums.AuditEntityType;
import com.bsolz.lms.audit.service.AuditLogFilter;
import com.bsolz.lms.audit.service.AuditQueryService;
import com.bsolz.lms.audit.web.dto.ActivityResponse;
import com.bsolz.lms.shared.web.PageResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The audit trail ({@code AUDIT_VIEW}) and the dashboard's recent-activity feed (everyone, limited to the
 * employees they may see and their own actions).
 */
@RestController
@RequiredArgsConstructor
class AuditController {

	private final AuditQueryService service;

	/** Newest first. {@code from}/{@code to} are days in the tenant's timezone, both inclusive. */
	@GetMapping("/api/v1/audit-logs")
	@PreAuthorize("hasAuthority('AUDIT_VIEW')")
	PageResponse<ActivityResponse> search(@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to, @RequestParam(required = false) UUID actorUserId,
			@RequestParam(required = false) UUID employeeId, @RequestParam(required = false) AuditAction action,
			@RequestParam(required = false) AuditEntityType entityType, @RequestParam(required = false) UUID entityId,
			@PageableDefault(size = 50) Pageable pageable) {
		return PageResponse.from(service.search(
				new AuditLogFilter(from, to, actorUserId, employeeId, action, entityType, entityId), pageable));
	}

	/** The latest entries, newest first, without details; {@code limit} is at most 50. */
	@GetMapping("/api/v1/dashboard/activities")
	List<ActivityResponse> activities(@RequestParam(defaultValue = "20") int limit) {
		return service.feed(limit);
	}

}
