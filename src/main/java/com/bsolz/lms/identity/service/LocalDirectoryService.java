package com.bsolz.lms.identity.service;

import com.bsolz.lms.identity.entity.AppUser;
import com.bsolz.lms.identity.repository.AppUserRepository;
import com.bsolz.lms.identity.web.dto.LocalTenantResponse;
import com.bsolz.lms.identity.web.dto.LocalUserResponse;
import com.bsolz.lms.shared.exception.ApiException;
import com.bsolz.lms.shared.exception.CommonErrorCode;
import com.bsolz.lms.shared.tenancy.TenantContext;
import com.bsolz.lms.shared.tenancy.TenantInfo;
import com.bsolz.lms.shared.tenancy.TenantRegistry;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Local development only: the tenants and users a developer can sign in as through
 * {@code POST /local/tokens/tenant}, so the frontends can offer a "sign in as" picker instead of
 * copying subjects from the log.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBooleanProperty("lms.security.local-issuer.enabled")
public class LocalDirectoryService {

	private final TenantRegistry tenantRegistry;

	private final AppUserRepository userRepository;

	private final TransactionTemplate transactionTemplate;

	public List<LocalTenantResponse> tenants() {
		return tenantRegistry.findAllServing().stream()
				.sorted(Comparator.comparing(TenantInfo::key))
				.map(tenant -> new LocalTenantResponse(tenant.id(), tenant.key()))
				.toList();
	}

	/** Users that can sign in: not disabled, and known to the identity provider. */
	public List<LocalUserResponse> users(UUID tenantId) {
		TenantInfo tenant = tenantRegistry.findById(tenantId)
				.filter(TenantInfo::isServing)
				.orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "Tenant not found"));
		return TenantContext.call(tenant, () -> transactionTemplate.execute(status -> userRepository
				.findAll(Sort.by("email")).stream()
				.filter(user -> !user.isDisabled() && user.getIdpSubject() != null)
				.map(LocalDirectoryService::toResponse)
				.toList()));
	}

	private static LocalUserResponse toResponse(AppUser user) {
		return new LocalUserResponse(user.getId(), user.getEmail(), user.getIdpSubject(), user.getStatus(),
				user.getRoleCodes());
	}

}
