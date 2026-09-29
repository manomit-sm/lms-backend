package com.bsolz.lms;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Architecture rules that keep tenancy safe, beyond module boundaries (see {@link ModularityTests}).
 */
class ArchitectureRulesTests {

	private static final JavaClasses CLASSES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("com.bsolz.lms");

	/**
	 * A raw DataSource bypasses tenant routing. Only the tenancy infrastructure, the shared configuration
	 * (job locks in {@code public}) and the migration runner (which targets schemas explicitly) may use it;
	 * everything else goes through JPA or the tenant-aware JdbcTemplate.
	 */
	@Test
	void onlyTenancyInfrastructureUsesTheRawDataSource() {
		noClasses().that().resideOutsideOfPackages("com.bsolz.lms.shared.tenancy..", "com.bsolz.lms.shared.config..")
				.and().doNotHaveFullyQualifiedName("com.bsolz.lms.platform.service.TenantMigrationService")
				.should().dependOnClassesThat().areAssignableTo(DataSource.class)
				.because("the tenant schema is selected by the tenant-aware DataSource and Hibernate's tenant routing")
				.check(CLASSES);
	}

	/**
	 * Work on threads the application doesn't manage runs without the tenant (and security context)
	 * propagation of the task executor's decorator; use {@code @Async}, the Spring task executor or
	 * {@code TenantJobRunner} instead.
	 */
	@Test
	void noUnmanagedThreadsOrExecutors() {
		noClasses().should().callConstructorWhere(target(Thread.class))
				.orShould().callMethodWhere(staticThreadFactories())
				.orShould().dependOnClassesThat().belongToAnyOf(Executors.class, ForkJoinPool.class,
						ThreadPoolExecutor.class)
				.orShould().callMethod(CompletableFuture.class, "runAsync", Runnable.class)
				.orShould().callMethod(CompletableFuture.class, "supplyAsync", Supplier.class)
				.because("tenant context only follows work run by the managed, decorated task executor")
				.check(CLASSES);
	}

	/** The tenant comes only from the validated token, never from a header the client controls. */
	@Test
	void controllersNeverReadHeaders() {
		noClasses().that().areAnnotatedWith(RestController.class)
				.should().callMethod(HttpServletRequest.class, "getHeader", String.class)
				.orShould().callMethod(HttpServletRequest.class, "getHeaders", String.class)
				.because("the tenant must come from the access token only")
				.check(CLASSES);
		methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
				.should(haveNoParameterAnnotatedWith(RequestHeader.class))
				.because("the tenant must come from the access token only")
				.check(CLASSES);
	}

	private static DescribedPredicate<JavaCall<?>> target(Class<?> owner) {
		return DescribedPredicate.describe("target is a " + owner.getSimpleName(),
				call -> call.getTargetOwner().isAssignableTo(owner));
	}

	private static DescribedPredicate<JavaCall<?>> staticThreadFactories() {
		Set<String> factories = Set.of("startVirtualThread", "ofVirtual", "ofPlatform");
		return DescribedPredicate.describe("Thread.startVirtualThread/ofVirtual/ofPlatform",
				call -> call.getTargetOwner().isEquivalentTo(Thread.class) && factories.contains(call.getName()));
	}

	private static ArchCondition<JavaMethod> haveNoParameterAnnotatedWith(Class<? extends Annotation> annotation) {
		return new ArchCondition<>("have no parameter annotated with @" + annotation.getSimpleName()) {
			@Override
			public void check(JavaMethod method, ConditionEvents events) {
				for (JavaParameter parameter : method.getParameters()) {
					if (parameter.isAnnotatedWith(annotation)) {
						events.add(SimpleConditionEvent.violated(method,
								method.getFullName() + " reads a header with @" + annotation.getSimpleName()));
					}
				}
			}
		};
	}

}
