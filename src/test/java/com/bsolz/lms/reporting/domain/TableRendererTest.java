package com.bsolz.lms.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TableRendererTest {

	private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

	@Test
	void csvQuotesWhatNeedsQuotingAndDefusesFormulas() {
		ReportTable table = new ReportTable("T", List.of("Name", "Days", "Note", "Date", "At"), List.of(
				Arrays.asList("Doe, Jane", new BigDecimal("2.50"), "=HYPERLINK(\"x\")", LocalDate.of(2026, 3, 1),
						Instant.parse("2026-03-01T09:30:00Z")),
				Arrays.asList("-Minus", 3, null, null, null)));

		String csv = new String(TableRenderer.csv(table, BRUSSELS), StandardCharsets.UTF_8);

		assertThat(csv).isEqualTo("﻿Name,Days,Note,Date,At\r\n"
				+ "\"Doe, Jane\",2.5,\"'=HYPERLINK(\"\"x\"\")\",2026-03-01,2026-03-01T10:30\r\n"
				+ "'-Minus,3,,,\r\n");
	}

}
