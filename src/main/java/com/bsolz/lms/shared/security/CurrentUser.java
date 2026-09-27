package com.bsolz.lms.shared.security;

import com.bsolz.lms.shared.exception.ApiException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the authenticated tenant user of the current request. */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static Optional<LmsPrincipal> find() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getPrincipal() instanceof LmsPrincipal principal
				? Optional.of(principal)
				: Optional.empty();
	}

	public static LmsPrincipal require() {
		return find().orElseThrow(
				() -> new ApiException(SecurityErrorCode.UNAUTHENTICATED, "No authenticated tenant user"));
	}

}
