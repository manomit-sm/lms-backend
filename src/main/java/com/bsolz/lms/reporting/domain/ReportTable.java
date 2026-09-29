package com.bsolz.lms.reporting.domain;

import java.util.List;

/**
 * A report as a table, for export. Cells are strings, numbers ({@link java.math.BigDecimal},
 * {@link Integer}, {@link Long}), dates ({@link java.time.LocalDate}), instants or null.
 */
public record ReportTable(String title, List<String> headers, List<List<Object>> rows) {
}
