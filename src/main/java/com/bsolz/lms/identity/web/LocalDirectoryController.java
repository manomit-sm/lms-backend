package com.bsolz.lms.identity.web;

import com.bsolz.lms.identity.service.LocalDirectoryService;
import com.bsolz.lms.identity.web.dto.LocalTenantResponse;
import com.bsolz.lms.identity.web.dto.LocalUserResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local development only, next to {@code /local/tokens}: who a developer can sign in as. Unauthenticated,
 * like the token endpoint, and absent unless the local issuer is enabled.
 */
@RestController
@RequestMapping("/local/directory")
@RequiredArgsConstructor
@ConditionalOnBooleanProperty("lms.security.local-issuer.enabled")
class LocalDirectoryController {

	private final LocalDirectoryService directoryService;

	@GetMapping("/tenants")
	List<LocalTenantResponse> tenants() {
		return directoryService.tenants();
	}

	@GetMapping("/tenants/{tenantId}/users")
	List<LocalUserResponse> users(@PathVariable UUID tenantId) {
		return directoryService.users(tenantId);
	}

}
