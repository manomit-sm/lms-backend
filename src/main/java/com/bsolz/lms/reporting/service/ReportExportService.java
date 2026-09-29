package com.bsolz.lms.reporting.service;

import com.bsolz.lms.reporting.api.ReportExportRequested;
import com.bsolz.lms.reporting.domain.ReportScope;
import com.bsolz.lms.reporting.domain.ReportTable;
import com.bsolz.lms.reporting.domain.TableRenderer;
import com.bsolz.lms.reporting.entity.ReportExport;
import com.bsolz.lms.reporting.exception.ReportErrorCode;
import com.bsolz.lms.reporting.model.enums.ExportFormat;
import com.bsolz.lms.reporting.model.enums.ExportStatus;
import com.bsolz.lms.reporting.repository.ReportExportRepository;
import com.bsolz.lms.reporting.web.dto.ExportRequest;
import com.bsolz.lms.reporting.web.dto.ExportResponse;
import com.bsolz.lms.settings.api.SettingsApi;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.security.CurrentUser;
import com.bsolz.lms.shared.storage.FileStorage;
import com.bsolz.lms.shared.storage.PresignedDownload;
import com.bsolz.lms.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Report exports: requested by a user (who must hold REPORT_EXPORT), generated asynchronously after the
 * request commits, stored in object storage and downloaded with a presigned URL. The requester's report
 * scope and the resolved filters are saved with the request, because generation runs without them.
 * Only the requester sees their exports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExportService {

	/** A running export not finished after this long was interrupted (e.g. by a restart) and may run again. */
	static final Duration STALE_RUNNING = Duration.ofMinutes(15);

	private final ReportExportRepository repository;

	private final ReportService reports;

	private final ReportTables tables;

	private final FileStorage storage;

	private final SettingsApi settings;

	private final ApplicationEventPublisher events;

	private final TransactionTemplate transactionTemplate;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	@Transactional
	public ExportResponse request(ExportRequest request) {
		UUID userId = CurrentUser.require().userId();
		ReportScope scope = reports.currentScope();
		ReportCriteria criteria = reports.resolve(scope, request.from(), request.to(), request.employeeId(),
				request.departmentId(), request.leaveTypeId(), request.leavePeriodId(), request.status());
		ReportExport export = repository.save(new ReportExport(userId, request.report(), request.format(),
				jsonMapper.writeValueAsString(criteria), scope));
		events.publishEvent(new ReportExportRequested(TenantContext.require().id(), export.getId()));
		return toResponse(export);
	}

	@Transactional(readOnly = true)
	public ExportResponse get(UUID id) {
		return toResponse(repository.findByIdAndRequestedByUserId(id, CurrentUser.require().userId())
				.orElseThrow(() -> new ApiException(ReportErrorCode.EXPORT_NOT_FOUND, "Export not found")));
	}

	@Transactional(readOnly = true)
	public List<ExportResponse> mine() {
		return repository.findTop20ByRequestedByUserIdOrderByCreatedAtDesc(CurrentUser.require().userId()).stream()
				.map(this::toResponse)
				.toList();
	}

	/**
	 * Generates a pending (or interrupted) export, with its tenant bound. A failure is recorded on the export
	 * rather than retried: the requester sees it and can ask again.
	 */
	public void generate(UUID exportId) {
		ReportExport export = transactionTemplate.execute(status -> repository.findById(exportId)
				.filter(found -> found.getStatus() == ExportStatus.PENDING
						|| found.getStatus() == ExportStatus.RUNNING
								&& found.getUpdatedAt().isBefore(now().minus(STALE_RUNNING)))
				.map(found -> {
					found.start();
					return found;
				})
				.orElse(null));
		if (export == null) {
			return;
		}
		try {
			ReportCriteria criteria = jsonMapper.readValue(export.getParameters(), ReportCriteria.class);
			ZoneId zone = settings.current().timezone();
			ReportTable table = transactionTemplate.execute(
					status -> tables.build(export.getReport(), export.scope(), criteria));
			byte[] content = export.getFormat() == ExportFormat.CSV ? TableRenderer.csv(table, zone)
					: TableRenderer.xlsx(table, zone);
			String fileName = export.getReport().name().toLowerCase(Locale.ROOT).replace('_', '-') + "-"
					+ criteria.from() + "-to-" + criteria.to() + "." + export.getFormat().extension();
			String key = "tenants/" + TenantContext.require().id() + "/report-exports/" + exportId + "/" + fileName;
			storage.put(key, content, export.getFormat().contentType());
			update(exportId, found -> found.complete(fileName, key, table.rows().size(), content.length, now()));
		}
		catch (ApiException ex) {
			update(exportId, found -> found.fail(ex.getMessage(), now()));
		}
		catch (RuntimeException ex) {
			log.error("Report export {} failed", exportId, ex);
			update(exportId, found -> found.fail("The export failed; please try again", now()));
		}
	}

	private void update(UUID exportId, Consumer<ReportExport> change) {
		transactionTemplate.executeWithoutResult(status -> change.accept(repository.findById(exportId).orElseThrow()));
	}

	private ExportResponse toResponse(ReportExport export) {
		PresignedDownload download = export.getStatus() == ExportStatus.COMPLETED
				? storage.presignDownload(export.getStorageKey(), export.getFileName())
				: null;
		return new ExportResponse(export.getId(), export.getReport(), export.getFormat(), export.getStatus(),
				export.getFileName(), export.getRowCount(), export.getSizeBytes(), export.getError(),
				export.getCreatedAt(), export.getCompletedAt(), download == null ? null : download.url(),
				download == null ? null : download.expiresAt());
	}

	private Instant now() {
		return Instant.now(clock);
	}

}
