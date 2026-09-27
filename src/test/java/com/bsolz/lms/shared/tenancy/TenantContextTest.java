package com.bsolz.lms.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TenantContextTest {

	private static final TenantInfo ACME = tenant("acme");

	private static final TenantInfo GLOBEX = tenant("globex");

	@Test
	void nothingIsBoundByDefault() {
		assertThat(TenantContext.current()).isEmpty();
		assertThat(TenantSchemas.currentSchema()).isEqualTo(TenantSchemas.PUBLIC_SCHEMA);
		assertThatIllegalStateException().isThrownBy(TenantContext::require);
	}

	@Test
	void bindsForTheDurationOfTheTaskOnly() {
		TenantContext.run(ACME, () -> {
			assertThat(TenantContext.require()).isEqualTo(ACME);
			assertThat(TenantSchemas.currentSchema()).isEqualTo("tenant_acme");
			assertThat(MDC.get(TenantContext.MDC_KEY)).isEqualTo("acme");
		});
		assertThat(TenantContext.current()).isEmpty();
		assertThat(MDC.get(TenantContext.MDC_KEY)).isNull();
	}

	@Test
	void nestedBindingIsRestoredAfterwards() throws Exception {
		String result = TenantContext.call(ACME, () -> {
			TenantContext.run(GLOBEX, () -> assertThat(TenantContext.require()).isEqualTo(GLOBEX));
			assertThat(MDC.get(TenantContext.MDC_KEY)).isEqualTo("acme");
			return TenantContext.require().key();
		});
		assertThat(result).isEqualTo("acme");
	}

	@Test
	void isNotVisibleToOtherThreadsUnlessPropagated() throws Exception {
		AtomicReference<Boolean> boundOnOtherThread = new AtomicReference<>();
		AtomicReference<String> propagated = new AtomicReference<>();
		TenantTaskDecorator decorator = new TenantTaskDecorator();
		TenantContext.run(ACME, () -> {
			Thread plain = Thread.ofVirtual().start(() -> boundOnOtherThread.set(TenantContext.current().isPresent()));
			Thread decorated = Thread.ofVirtual()
					.start(decorator.decorate(() -> propagated.set(TenantContext.require().key())));
			join(plain);
			join(decorated);
		});
		assertThat(boundOnOtherThread.get()).isFalse();
		assertThat(propagated.get()).isEqualTo("acme");
	}

	private static void join(Thread thread) {
		try {
			thread.join();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private static TenantInfo tenant(String key) {
		return new TenantInfo(UUID.randomUUID(), key, TenantSchemas.schemaFor(key), TenantStatus.ACTIVE,
				SchemaMigrationState.UP_TO_DATE, ZoneId.of("UTC"));
	}

}
