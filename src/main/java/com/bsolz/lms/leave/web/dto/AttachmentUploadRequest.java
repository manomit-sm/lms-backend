package com.bsolz.lms.leave.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * @param contentType application/pdf, image/jpeg or image/png
 * @param sizeBytes at most 10 MiB; the upload must be exactly this size
 */
public record AttachmentUploadRequest(@NotBlank @Size(max = 255) String fileName, @NotBlank String contentType,
		@NotNull @Positive @Max(10 * 1024 * 1024) Long sizeBytes) {
}
