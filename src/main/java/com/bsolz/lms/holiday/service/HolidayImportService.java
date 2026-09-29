package com.bsolz.lms.holiday.service;

import com.bsolz.lms.holiday.entity.Holiday;
import com.bsolz.lms.holiday.exception.HolidayErrorCode;
import com.bsolz.lms.holiday.model.enums.HolidayType;
import com.bsolz.lms.holiday.repository.HolidayRepository;
import com.bsolz.lms.holiday.web.dto.HolidayImportResult;
import com.bsolz.lms.organization.api.OrganizationApi;
import com.bsolz.lms.organization.model.enums.OrgUnitType;
import com.bsolz.lms.shared.exception.ApiException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;

/**
 * Imports holidays from CSV, all or nothing. The header row names the columns, in any order:
 * <pre>
 * date,name,type,description,locations,departments
 * 2026-12-25,Christmas Day,PUBLIC,,BLR;LON,
 * </pre>
 * {@code date} (ISO {@code yyyy-MM-dd}) and {@code name} are required; {@code type} defaults to PUBLIC;
 * {@code locations} and {@code departments} are {@code ;}-separated codes (empty: everyone). A row
 * matching an existing holiday (same date and name) is skipped. If any row is invalid nothing is
 * imported and the problem lists every invalid row.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class HolidayImportService {

	static final int MAX_ROWS = 1000;

	private static final CsvMapper CSV = new CsvMapper();

	private final HolidayRepository repository;

	private final OrganizationApi organizationApi;

	public HolidayImportResult importCsv(InputStream csv) {
		List<Map<String, String>> rows = read(csv);
		Map<String, UUID> locations = organizationApi.findUnitIdsByCode(OrgUnitType.LOCATION,
				allCodes(rows, "locations"));
		Map<String, UUID> departments = organizationApi.findUnitIdsByCode(OrgUnitType.DEPARTMENT,
				allCodes(rows, "departments"));

		List<Map<String, Object>> errors = new ArrayList<>();
		Map<String, Integer> firstRowByKey = new HashMap<>();
		List<Holiday> toCreate = new ArrayList<>();
		int skipped = 0;
		for (int i = 0; i < rows.size(); i++) {
			int rowNumber = i + 2; // the header is row 1
			Map<String, String> row = rows.get(i);
			List<String> problems = new ArrayList<>();
			LocalDate date = parseDate(row.get("date"), problems);
			String name = row.get("name");
			if (name == null) {
				problems.add("name is required");
			}
			else if (name.length() > 150) {
				problems.add("name is longer than 150 characters");
			}
			HolidayType type = parseType(row.get("type"), problems);
			String description = row.get("description");
			if (description != null && description.length() > 500) {
				problems.add("description is longer than 500 characters");
			}
			Set<UUID> locationIds = resolveCodes(row.get("locations"), locations, "location", problems);
			Set<UUID> departmentIds = resolveCodes(row.get("departments"), departments, "department", problems);
			if (problems.isEmpty()) {
				Integer duplicateOf = firstRowByKey.putIfAbsent(date + "|" + name.toLowerCase(Locale.ROOT), rowNumber);
				if (duplicateOf != null) {
					problems.add("duplicates row " + duplicateOf);
				}
			}
			if (!problems.isEmpty()) {
				errors.add(Map.of("row", rowNumber, "message", String.join("; ", problems)));
				continue;
			}
			if (repository.existsByDateAndNameIgnoreCase(date, name)) {
				skipped++;
				continue;
			}
			Holiday holiday = new Holiday();
			holiday.setName(name);
			holiday.setDate(date);
			holiday.setType(type);
			holiday.setDescription(description);
			holiday.getLocationIds().addAll(locationIds);
			holiday.getDepartmentIds().addAll(departmentIds);
			toCreate.add(holiday);
		}
		if (!errors.isEmpty()) {
			throw new ApiException(HolidayErrorCode.INVALID_HOLIDAY_IMPORT,
					errors.size() + " row(s) are invalid; nothing was imported", Map.of("errors", errors));
		}
		repository.saveAll(toCreate);
		return new HolidayImportResult(toCreate.size(), skipped);
	}

	/** Rows as maps keyed by lower-cased header, with values trimmed and blanks as null. */
	private static List<Map<String, String>> read(InputStream csv) {
		List<Map<String, String>> rows = new ArrayList<>();
		try (MappingIterator<Map<String, String>> iterator = CSV.readerForMapOf(String.class)
				.with(CsvSchema.emptySchema().withHeader())
				.readValues(csv)) {
			while (iterator.hasNextValue()) {
				if (rows.size() == MAX_ROWS) {
					throw invalid("A file may hold at most " + MAX_ROWS + " holidays");
				}
				Map<String, String> row = new LinkedHashMap<>();
				iterator.nextValue().forEach((key, value) -> row.put(key.trim().toLowerCase(Locale.ROOT),
						value == null || value.isBlank() ? null : value.trim()));
				rows.add(row);
			}
		}
		catch (JacksonException ex) {
			throw invalid("The file is not valid CSV with a header row");
		}
		if (rows.isEmpty()) {
			throw invalid("The file has no holidays");
		}
		if (!rows.getFirst().containsKey("date") || !rows.getFirst().containsKey("name")) {
			throw invalid("The header row must include the columns date and name");
		}
		return rows;
	}

	private static Set<String> allCodes(List<Map<String, String>> rows, String column) {
		return rows.stream().flatMap(row -> splitCodes(row.get(column)).stream()).collect(Collectors.toSet());
	}

	private static List<String> splitCodes(String value) {
		return value == null ? List.of()
				: Arrays.stream(value.split(";")).map(String::trim).filter(code -> !code.isEmpty())
						.map(code -> code.toUpperCase(Locale.ROOT)).toList();
	}

	private static Set<UUID> resolveCodes(String value, Map<String, UUID> idsByCode, String kind, List<String> problems) {
		Set<UUID> ids = new HashSet<>();
		for (String code : splitCodes(value)) {
			UUID id = idsByCode.get(code);
			if (id == null) {
				problems.add("unknown " + kind + " code " + code);
			}
			else {
				ids.add(id);
			}
		}
		return ids;
	}

	private static LocalDate parseDate(String value, List<String> problems) {
		if (value == null) {
			problems.add("date is required");
			return null;
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException ex) {
			problems.add("date '" + value + "' is not yyyy-MM-dd");
			return null;
		}
	}

	private static HolidayType parseType(String value, List<String> problems) {
		if (value == null) {
			return HolidayType.PUBLIC;
		}
		try {
			return HolidayType.valueOf(value.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			problems.add("type must be one of " + Arrays.toString(HolidayType.values()));
			return null;
		}
	}

	private static ApiException invalid(String message) {
		return new ApiException(HolidayErrorCode.INVALID_HOLIDAY_IMPORT, message);
	}

}
