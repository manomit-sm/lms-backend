package com.bsolz.lms.leave.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelLeaveRequest(@NotBlank @Size(max = 1000) String reason) {
}
