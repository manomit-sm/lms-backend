package com.bsolz.lms.platform.web.dto;

import com.bsolz.lms.shared.tenancy.TenantSchemas;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param key permanent tenant key; the schema name is derived from it ({@code acme-corp} → {@code tenant_acme_corp})
 * @param subdomain optional, for {@code <subdomain>.<app domain>} login routing
 * @param defaultTimezone IANA zone id, e.g. {@code Europe/Brussels}
 * @param adminEmail the first tenant admin, invited once the schema is ready
 */
public record CreateTenantRequest(
		@NotBlank @Pattern(regexp = TenantSchemas.TENANT_KEY_REGEX,
				message = "must be 3-40 lowercase letters, digits or hyphens, starting with a letter") String key,
		@NotBlank @Size(max = 200) String name,
		@Pattern(regexp = "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$", message = "must be a valid DNS label") String subdomain,
		@NotBlank String defaultTimezone, @NotBlank @Email @Size(max = 254) String adminEmail) {
}
