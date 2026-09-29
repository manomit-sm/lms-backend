package com.bsolz.lms.holiday.web.dto;

/**
 * @param created holidays added
 * @param skipped rows for a holiday that already exists (same date and name)
 */
public record HolidayImportResult(int created, int skipped) {
}
