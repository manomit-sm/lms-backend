package com.bsolz.lms.platform.web;

import com.bsolz.lms.platform.entity.Tenant;
import com.bsolz.lms.platform.mapper.TenantMapper;
import com.bsolz.lms.platform.service.TenantProvisioningService;
import com.bsolz.lms.platform.web.dto.AddTenantAdminRequest;
import com.bsolz.lms.platform.web.dto.CreateTenantRequest;
import com.bsolz.lms.platform.web.dto.TenantResponse;
import com.bsolz.lms.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant management for platform administrators (secured by the platform filter chain). */
@RestController
@RequestMapping("/platform/tenants")
@RequiredArgsConstructor
class PlatformTenantController {

	private final TenantProvisioningService provisioningService;

	private final TenantMapper tenantMapper;

	@PostMapping
	ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
		Tenant tenant = provisioningService.provision(request);
		return ResponseEntity.created(URI.create("/platform/tenants/" + tenant.getId()))
				.body(tenantMapper.toResponse(tenant));
	}

	@GetMapping
	PageResponse<TenantResponse> list(
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(provisioningService.list(pageable).map(tenantMapper::toResponse));
	}

	@GetMapping("/{tenantId}")
	TenantResponse get(@PathVariable UUID tenantId) {
		return tenantMapper.toResponse(provisioningService.get(tenantId));
	}

	@PostMapping("/{tenantId}/suspend")
	TenantResponse suspend(@PathVariable UUID tenantId) {
		return tenantMapper.toResponse(provisioningService.suspend(tenantId));
	}

	@PostMapping("/{tenantId}/activate")
	TenantResponse activate(@PathVariable UUID tenantId) {
		return tenantMapper.toResponse(provisioningService.activate(tenantId));
	}

	@PostMapping("/{tenantId}/admins")
	ResponseEntity<Map<String, UUID>> addAdmin(@PathVariable UUID tenantId,
			@Valid @RequestBody AddTenantAdminRequest request) {
		return ResponseEntity.ok(Map.of("userId", provisioningService.addAdmin(tenantId, request.email())));
	}

	@PostMapping("/{tenantId}/migrate")
	TenantResponse migrate(@PathVariable UUID tenantId) {
		return tenantMapper.toResponse(provisioningService.migrate(tenantId));
	}

}
