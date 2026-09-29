package com.bsolz.lms.reporting.entity;

import com.bsolz.lms.reporting.domain.ReportScope;
import com.bsolz.lms.reporting.model.enums.ExportFormat;
import com.bsolz.lms.reporting.model.enums.ExportStatus;
import com.bsolz.lms.reporting.model.enums.ReportType;
import com.bsolz.lms.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

/** A requested report export; see {@code ReportExportService}. */
@Getter
@Entity
@Table(name = "report_export")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportExport extends BaseEntity {

	private UUID requestedByUserId;

	@Enumerated(EnumType.STRING)
	private ReportType report;

	@Enumerated(EnumType.STRING)
	private ExportFormat format;

	/** The resolved report criteria, as JSON. */
	@Column(columnDefinition = "jsonb")
	@ColumnTransformer(write = "?::jsonb")
	private String parameters;

	private boolean scopeTenantWide;

	private UUID scopeManagerEmployeeId;

	@Enumerated(EnumType.STRING)
	private ExportStatus status;

	private String fileName;

	private String storageKey;

	private Integer rowCount;

	private Long sizeBytes;

	private String error;

	private Instant completedAt;

	public ReportExport(UUID requestedByUserId, ReportType report, ExportFormat format, String parameters,
			ReportScope scope) {
		this.requestedByUserId = requestedByUserId;
		this.report = report;
		this.format = format;
		this.parameters = parameters;
		this.scopeTenantWide = scope.tenantWide();
		this.scopeManagerEmployeeId = scope.managerEmployeeId();
		this.status = ExportStatus.PENDING;
	}

	public ReportScope scope() {
		return new ReportScope(scopeTenantWide, scopeManagerEmployeeId);
	}

	public void start() {
		status = ExportStatus.RUNNING;
	}

	public void complete(String fileName, String storageKey, int rowCount, long sizeBytes, Instant at) {
		this.status = ExportStatus.COMPLETED;
		this.fileName = fileName;
		this.storageKey = storageKey;
		this.rowCount = rowCount;
		this.sizeBytes = sizeBytes;
		this.completedAt = at;
	}

	public void fail(String error, Instant at) {
		this.status = ExportStatus.FAILED;
		this.error = error.length() <= 1000 ? error : error.substring(0, 997) + "...";
		this.completedAt = at;
	}

}
