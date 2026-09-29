package com.bsolz.lms.reporting.domain;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * Renders report tables as CSV (UTF-8 with a byte order mark, so Excel reads the encoding) or XLSX
 * (streamed, so large exports don't hold every row in memory). Instants are shown in the tenant's zone.
 */
public final class TableRenderer {

	private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	private TableRenderer() {
	}

	/**
	 * RFC 4180 CSV. Text that a spreadsheet would treat as a formula ({@code = + - @}, tab, carriage return)
	 * is prefixed with an apostrophe, so an export can't smuggle formulas into someone's spreadsheet.
	 */
	public static byte[] csv(ReportTable table, ZoneId zone) {
		StringBuilder out = new StringBuilder();
		appendCsvRow(out, List.copyOf(table.headers()), zone);
		table.rows().forEach(row -> appendCsvRow(out, row, zone));
		byte[] text = out.toString().getBytes(StandardCharsets.UTF_8);
		byte[] result = new byte[UTF8_BOM.length + text.length];
		System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
		System.arraycopy(text, 0, result, UTF8_BOM.length, text.length);
		return result;
	}

	public static byte[] xlsx(ReportTable table, ZoneId zone) {
		try (SXSSFWorkbook workbook = new SXSSFWorkbook(200); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet(sheetName(table.title()));
			CellStyle header = workbook.createCellStyle();
			Font bold = workbook.createFont();
			bold.setBold(true);
			header.setFont(bold);
			CellStyle date = workbook.createCellStyle();
			date.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
			CellStyle dateTime = workbook.createCellStyle();
			dateTime.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm"));

			Row headerRow = sheet.createRow(0);
			for (int column = 0; column < table.headers().size(); column++) {
				Cell cell = headerRow.createCell(column);
				cell.setCellValue(table.headers().get(column));
				cell.setCellStyle(header);
				sheet.setColumnWidth(column, Math.max(12, table.headers().get(column).length() + 4) * 256);
			}
			sheet.createFreezePane(0, 1);
			int rowIndex = 1;
			for (List<Object> values : table.rows()) {
				Row row = sheet.createRow(rowIndex++);
				for (int column = 0; column < values.size(); column++) {
					Object value = values.get(column);
					if (value == null) {
						continue;
					}
					Cell cell = row.createCell(column);
					switch (value) {
						case BigDecimal number -> cell.setCellValue(number.doubleValue());
						case Number number -> cell.setCellValue(number.doubleValue());
						case LocalDate day -> {
							cell.setCellValue(day);
							cell.setCellStyle(date);
						}
						case Instant instant -> {
							cell.setCellValue(LocalDateTime.ofInstant(instant, zone));
							cell.setCellStyle(dateTime);
						}
						default -> cell.setCellValue(value.toString());
					}
				}
			}
			workbook.write(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void appendCsvRow(StringBuilder out, List<?> values, ZoneId zone) {
		for (int column = 0; column < values.size(); column++) {
			if (column > 0) {
				out.append(',');
			}
			out.append(csvCell(values.get(column), zone));
		}
		out.append("\r\n");
	}

	private static String csvCell(Object value, ZoneId zone) {
		if (value == null) {
			return "";
		}
		String text = switch (value) {
			case BigDecimal number -> number.stripTrailingZeros().toPlainString();
			case Instant instant -> LocalDateTime.ofInstant(instant, zone).withNano(0).toString();
			default -> value.toString();
		};
		if (value instanceof String && !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
			text = "'" + text;
		}
		if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
			text = "\"" + text.replace("\"", "\"\"") + "\"";
		}
		return text;
	}

	/** Excel sheet names: at most 31 characters, none of {@code []:*?/\}. */
	private static String sheetName(String title) {
		String name = title.replaceAll("[\\[\\]:*?/\\\\]", " ");
		return name.length() <= 31 ? name : name.substring(0, 31);
	}

}
