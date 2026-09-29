# 02 — Modules, Package Layout, Liquibase Layout

## Modules

Every direct sub-package of `com.bsolz.lms` is a Spring Modulith application module. Each module's rules live in its `package-info.java` (`@ApplicationModule`), and its public API is its `api` sub-package (`@NamedInterface("api")`).

| Module | Owns | May depend on |
|---|---|---|
| `shared` | Shared kernel: base entity, error handling, web helpers, multi-tenancy (`tenancy`) and security (`security`) infrastructure | nothing (open module — every module may use it) |
| `organization` | Departments, designations, locations, work schedules, employees, reporting hierarchy | `shared` |
| `settings` | Tenant-level system settings (leave year start, timezone, date format, branding) | `shared` |
| `identity` | Users mapped to Cognito subjects, roles, permissions, identity-provider user lifecycle, `/auth/me` | `shared`, `organization` |
| `platform` | Tenant registry (public schema), provisioning and lifecycle, tenant migrations, platform admin API | `shared`, `identity` |
| `leavepolicy` | Leave types, leave policies and applicability rules, leave periods | `shared`, `organization`, `settings` |
| `holiday` | Holidays and their department/location applicability, CSV import | `shared`, `organization`, `settings` |
| `balance` | Leave balances and the balance ledger; allocation, accrual, carry-forward and expiry | `shared`, `organization`, `leavepolicy`, `settings` |
| `approval` | Approval workflow definitions, runtime instances and tasks, delegation, escalation | `shared`, `organization`, `identity`, `leavepolicy` |
| `leave` | Leave requests, per-day breakdown, attachments, status history, leave status machine | `shared`, `organization`, `leavepolicy`, `balance`, `holiday`, `approval`, `settings` |
| `calendar` | Read-only calendar views: leaves, holidays, team availability | `shared`, `organization`, `leavepolicy`, `leave`, `holiday` |
| `notification` | In-app notifications (SSE) driven by domain events; email later | `shared`, `identity`, `organization`, `leave`, `approval`, `balance` |
| `reporting` | Dashboard statistics, reports, asynchronous exports | `shared`, `organization`, `leavepolicy`, `holiday`, `balance`, `leave`, `approval` |
| `audit` | Activity log and recent-activity feed, populated from domain events | `shared`, `identity`, `organization`, `settings`, `leavepolicy`, `holiday`, `balance`, `approval`, `leave` |

"May depend on" means that module's `api` package only (except `shared`). The graph is acyclic:

```
organization, settings                 ← foundations
identity → organization                platform → identity
leavepolicy → organization, settings   holiday → organization, settings
balance → organization, leavepolicy, settings
approval → organization, identity, leavepolicy
leave → organization, leavepolicy, balance, holiday, approval, settings
calendar, notification, reporting, audit  ← read-side consumers
```

`approval` deliberately does **not** depend on `leave`: `leave` starts an approval and passes in what the workflow needs (leave type, days), then reacts to approval events. That keeps the graph free of cycles. `approval` uses `leavepolicy` only to validate leave types named in workflow rules.

## Rules

Enforced by `ModularityTests` (`ApplicationModules.of(LmsApplication.class).verify()`), which runs on every build. The Java compiler does not enforce these — the test does.

- A module may only use another module's `api` package, and only modules listed in its `allowedDependencies`. Adding a dependency is a deliberate change to `package-info.java`, reviewed like any other design change.
- Everything outside `api` is internal to its module.
- `shared` must never depend on a business module. When it needs module data (e.g. the tenant registry, the current user's permissions), it declares an interface that the owning module implements.
- Side effects (notifications, audit, email) are domain-event listeners (`@ApplicationModuleListener`), never direct calls, so a notification failure can never roll back a leave transaction. Events are stored in Modulith's event publication registry (JPA) and delivered after commit.
- The one deliberate exception: `leave` applies approval outcomes (`ApprovalCompleted` / `ApprovalRejected`) with a synchronous `@EventListener`, inside the approver's transaction. The decision, the leave status and the balance change are one unit of work - they commit or roll back together - so they are not a side effect.
- When two modules lock rows in one transaction, the order is fixed to avoid deadlocks: approval before leave request, leave request before balance.
- Domain events are records placed directly in the publishing module's `api` package (a named interface covers only its own package, not sub-packages), and every event carries the `tenantId`. Listeners bind the tenant from the event (`TenantExecutor`) rather than relying on the executing thread.
- Enums that appear in a module's `api` types live in `model/enums` and that package is also annotated `@NamedInterface("api")`, so it joins the module's public API.
- Keep internal classes package-private where Spring/JPA proxying allows, as a second line of defense.
- Data that belongs to an employee (balances, and later leave requests) is visible to exactly the employees the caller may see: every module checks `organization`'s `EmployeeVisibility` (self / reporting line / everyone) rather than re-deriving the scope.
- Other modules refer to another module's rows by id only (plain `UUID` columns in JPA, a foreign key in SQL), never through a JPA association across modules.

## Package layout inside a module

```
com.bsolz.lms.leave
├── package-info.java     # @ApplicationModule: display name, allowed dependencies
├── api/                  # PUBLIC: facade interfaces, DTOs, domain events (@NamedInterface("api"))
├── web/                  # REST controllers, request/response DTOs
├── service/              # application services — transaction boundaries
├── domain/               # domain logic (calculators, state machine, validators)
├── entity/               # JPA entities
├── model/enums/          # enums
├── repository/           # Spring Data repositories
└── mapper/               # MapStruct mappers
```

`shared` is organized by concern instead:

```
com.bsolz.lms.shared
├── package-info.java     # @ApplicationModule(type = OPEN)
├── config/               # clock, JPA auditing, scheduling + ShedLock
├── entity/               # BaseEntity (UUID id, created/updated at/by, @Version)
├── exception/            # ErrorCode, ApiException, ProblemDetail handler and writer
├── web/                  # paging helpers
├── validation/           # shared Bean Validation constraints (e.g. @HalfDays for leave-day amounts)
├── storage/              # object storage (S3, in-memory for local/tests) with presigned upload/download URLs
├── tenancy/              # tenant context, schema switching, tenant propagation to async work, jobs and events
└── security/             # filter chains, token validation, tenant filter, current user, permission codes, data scope
    └── local/            # self-signed token issuer for local development and tests only
```

This is a single Maven module (one `pom.xml`). `@Modulithic(sharedModules = "shared")` on `LmsApplication` makes `shared` part of every module-scoped integration test.

## Liquibase layout

Two changelog trees, because they migrate on different triggers: `public` on deploy; a tenant schema on deploy **and** when a new tenant is provisioned. Each changeset is a small YAML file whose only change is an `sqlFile`; the DDL itself lives in a plain `.sql` file.

```
src/main/resources/db/changelog/
├── db.changelog-master.yaml            # applied to every tenant schema
├── db.changelog-public.yaml            # applied to the public schema
├── changes/
│   ├── public/NNN-name.yaml            # one changeset each → sqlFile
│   └── tenant/NNN-name.yaml
└── sql/
    ├── public/NNN-name.sql             # the DDL
    └── tenant/NNN-name.sql
```

Conventions:

- YAML changelogs only, never XML. Ordering is explicit via `include` in the master changelog, never by directory scanning.
- `sqlFile` paths are relative to the classpath root (`relativeToChangelogFile: false`), with `splitStatements: false` and `stripComments: false`.
- Tenant SQL is **never schema-qualified**: the migration runner sets `search_path` to the tenant schema before applying it, so the same files build every tenant schema.
- Every changeset has a unique `id` and `author`; anything that isn't purely additive needs a rollback.
- Boot's Liquibase auto-run is disabled (`spring.liquibase.enabled: false`). `TenantMigrationService` applies the public changelog, then the tenant changelog to each tenant schema; each schema keeps its own Liquibase history table. A tenant whose migration fails is marked `FAILED` and returns 503 until retried, without blocking other tenants. A public-schema failure stops startup.
- Hibernate never touches the schema (`spring.jpa.hibernate.ddl-auto: none`).
- Modulith's `event_publication` table is created by Liquibase in `public` and in every tenant schema, not by Modulith.
- PostgreSQL extensions exist once per database, so they are created in the public changelog (`WITH SCHEMA public`). Tenant SQL refers to their objects schema-qualified (e.g. `public.gist_uuid_ops`), because a tenant connection's `search_path` holds only its own schema.
- A foreign key to another module's table (e.g. `leave_request.employee_id → employee.id`) is declared in the *dependent* module's changeset, never by editing the owning module's changesets.
