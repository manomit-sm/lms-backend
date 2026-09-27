package com.bsolz.lms.shared.tenancy;

import java.util.Optional;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Carries the submitting thread's tenant and security context into async work ({@code @Async},
 * Modulith {@code @ApplicationModuleListener}s). Spring Boot applies a single {@code TaskDecorator}
 * bean to its auto-configured task executor.
 */
public class TenantTaskDecorator implements TaskDecorator {

	@Override
	public Runnable decorate(Runnable runnable) {
		Optional<TenantInfo> tenant = TenantContext.current();
		Runnable withSecurity = new DelegatingSecurityContextRunnable(runnable, SecurityContextHolder.getContext());
		return tenant.<Runnable>map(bound -> () -> TenantContext.run(bound, withSecurity)).orElse(withSecurity);
	}

}
