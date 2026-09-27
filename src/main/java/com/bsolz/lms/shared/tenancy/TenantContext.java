package com.bsolz.lms.shared.tenancy;

import java.util.Optional;
import org.slf4j.MDC;

/**
 * The tenant bound to the current execution, held in a {@link ScopedValue}: bound for the
 * duration of a request (by {@code TenantContextFilter}), an async task ({@link TenantTaskDecorator})
 * or a per-tenant job iteration ({@link TenantJobRunner}), and unbound automatically afterwards -
 * it can't leak into the next request on a pooled or virtual thread.
 * <p>
 * Binding also puts the tenant key in the logging MDC under {@value #MDC_KEY}.
 */
public final class TenantContext {

	public static final String MDC_KEY = "tenant";

	private static final ScopedValue<TenantInfo> CURRENT = ScopedValue.newInstance();

	private TenantContext() {
	}

	public static Optional<TenantInfo> current() {
		return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
	}

	public static TenantInfo require() {
		if (!CURRENT.isBound()) {
			throw new IllegalStateException("No tenant is bound to the current execution");
		}
		return CURRENT.get();
	}

	public static void run(TenantInfo tenant, Runnable task) {
		ScopedValue.where(CURRENT, tenant).run(() -> {
			String previous = MDC.get(MDC_KEY);
			MDC.put(MDC_KEY, tenant.key());
			try {
				task.run();
			}
			finally {
				restoreMdc(previous);
			}
		});
	}

	public static <T, X extends Throwable> T call(TenantInfo tenant, ScopedValue.CallableOp<? extends T, X> task)
			throws X {
		return ScopedValue.where(CURRENT, tenant).call(() -> {
			String previous = MDC.get(MDC_KEY);
			MDC.put(MDC_KEY, tenant.key());
			try {
				return task.call();
			}
			finally {
				restoreMdc(previous);
			}
		});
	}

	private static void restoreMdc(String previous) {
		if (previous == null) {
			MDC.remove(MDC_KEY);
		}
		else {
			MDC.put(MDC_KEY, previous);
		}
	}

}
