/**
 * Shared kernel: base entity, error handling, web helpers, multi-tenancy infrastructure
 * ({@code tenancy}) and security infrastructure ({@code security}).
 * <p>
 * Open module: every module may use any of its types. It must not depend on any other module;
 * where it needs module data (e.g. the tenant registry or the current user's permissions) it
 * declares an interface here that the owning module implements.
 */
@ApplicationModule(
	displayName = "Shared Kernel",
	type = ApplicationModule.Type.OPEN
)
package com.bsolz.lms.shared;

import org.springframework.modulith.ApplicationModule;
