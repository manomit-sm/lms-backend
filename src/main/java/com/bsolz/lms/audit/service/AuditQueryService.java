package com.bsolz.lms.audit.service;

import com.bsolz.lms.audit.entity.ActivityLog;
import com.bsolz.lms.audit.repository.ActivityLogRepository;
import com.bsolz.lms.audit.web.dto.ActivityResponse;
import com.bsolz.lms.organization.api.EmployeeVisibility;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.settings.api.SettingsApi;
import com.bsolz.lms.shared.security.CurrentUser;
import com.bsolz.lms.shared.security.DataScope;
import com.bsolz.lms.shared.security.LmsPrincipal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the audit trail: the full, searchable log for AUDIT_VIEW, and a recent-activity feed limited to
 * the employees the caller may see (plus what they did themselves), without the entries' details.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuditQueryService {

	static final int MAX_FEED_SIZE = 50;

	private final ActivityLogRepository repository;

	private final EmployeeVisibility visibility;

	private final OrganizationApi organizationApi;

	private final SettingsApi settings;

	private final JsonMapper jsonMapper;

	public Page<ActivityResponse> search(AuditLogFilter filter, Pageable pageable) {
		Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("seq")));
		return repository.findAll(specification(filter), newestFirst).map(entry -> toResponse(entry, true));
	}

	public List<ActivityResponse> feed(int limit) {
		LmsPrincipal user = CurrentUser.require();
		Pageable first = PageRequest.of(0, Math.clamp(limit, 1, MAX_FEED_SIZE));
		Page<ActivityLog> entries;
		if (visibility.currentScope() == DataScope.TENANT) {
			entries = repository.findLatest(first);
		}
		else {
			Set<UUID> employees = new HashSet<>();
			if (user.employeeId() != null) {
				employees.add(user.employeeId());
				if (visibility.currentScope() == DataScope.TEAM) {
					employees.addAll(organizationApi.findReportingLine(user.employeeId()));
				}
			}
			// "in ()" is invalid SQL: a random id stands in for nobody
			entries = repository.findFeed(employees.isEmpty() ? Set.of(UUID.randomUUID()) : employees,
					user.userId(), first);
		}
		return entries.map(entry -> toResponse(entry, false)).getContent();
	}

	private Specification<ActivityLog> specification(AuditLogFilter filter) {
		ZoneId zone = settings.current().timezone();
		List<Specification<ActivityLog>> criteria = new ArrayList<>();
		if (filter.from() != null) {
			criteria.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"),
					filter.from().atStartOfDay(zone).toInstant()));
		}
		if (filter.to() != null) {
			criteria.add((root, query, cb) -> cb.lessThan(root.get("occurredAt"),
					filter.to().plusDays(1).atStartOfDay(zone).toInstant()));
		}
		if (filter.actorUserId() != null) {
			criteria.add((root, query, cb) -> cb.equal(root.get("actorUserId"), filter.actorUserId()));
		}
		if (filter.employeeId() != null) {
			criteria.add((root, query, cb) -> cb.equal(root.get("employeeId"), filter.employeeId()));
		}
		if (filter.action() != null) {
			criteria.add((root, query, cb) -> cb.equal(root.get("action"), filter.action()));
		}
		if (filter.entityType() != null) {
			criteria.add((root, query, cb) -> cb.equal(root.get("entityType"), filter.entityType()));
		}
		if (filter.entityId() != null) {
			criteria.add((root, query, cb) -> cb.equal(root.get("entityId"), filter.entityId()));
		}
		return Specification.allOf(criteria);
	}

	private ActivityResponse toResponse(ActivityLog entry, boolean withDetails) {
		return new ActivityResponse(entry.getId(), entry.getOccurredAt(),
				entry.getActorUserId() == null ? null
						: new ActivityResponse.Ref(entry.getActorUserId(), entry.getActorName()),
				entry.getAction(), entry.getEntityType(), entry.getEntityId(),
				entry.getEmployeeId() == null ? null
						: new ActivityResponse.Ref(entry.getEmployeeId(), entry.getEmployeeName()),
				entry.getSummary(),
				withDetails && entry.getDetails() != null ? jsonMapper.readTree(entry.getDetails()) : null);
	}

}
