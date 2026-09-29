package com.bsolz.lms.reporting.model.enums;

public enum ExportFormat {

	CSV("text/csv", "csv"),
	XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

	private final String contentType;

	private final String extension;

	ExportFormat(String contentType, String extension) {
		this.contentType = contentType;
		this.extension = extension;
	}

	public String contentType() {
		return contentType;
	}

	public String extension() {
		return extension;
	}

}
