package com.bsolz.lms.reporting.web.dto;

import java.util.UUID;

public record LeaveTypeRef(UUID id, String code, String name, String color) {
}
