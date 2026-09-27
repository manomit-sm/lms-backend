package com.bsolz.lms.shared.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable JSON shape for paged responses (Spring's {@code Page} serialization is not an API contract). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

}
