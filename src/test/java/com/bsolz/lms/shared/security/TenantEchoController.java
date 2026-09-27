package com.bsolz.lms.shared.security;

import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.shared.tenancy.TenantSchemas;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint exposing what the request sees after the tenant filter: bound tenant, principal, DB schema. */
@RestController
class TenantEchoController {

	private final JdbcTemplate jdbcTemplate;

	TenantEchoController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping("/api/test/whoami")
	Map<String, Object> whoami() {
		LmsPrincipal principal = CurrentUser.require();
		return Map.of("tenantKey", TenantContext.require().key(), "subject", principal.subject(), "schema",
				TenantSchemas.currentSchema(), "databaseSchema",
				jdbcTemplate.queryForObject("SELECT current_schema()", String.class));
	}

}
