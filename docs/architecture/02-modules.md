# 02 — Modules, Package Layout, Liquibase Layout

## Modules

| Module | Owns | Depends on (via `api` packages only) |
|---|---|---|
| `identity` | Mapping Cognito subjects to internal users, role assignment | `tenant` |
| `tenant` | Tenant registry (public schema), tenant lifecycle (provision/suspend/offboard), per-tenant migration status | — (foundation module) |
| `employee` | Employee records, org structure (manager chain), employment dates | `tenant`, `identity` |
| `leave` | Leave types, leave ledger, balance computation, leave requests | `employee`, `tenant` |
| `approval` | Approval workflow definitions, approval steps/decisions on a leave request | `leave`, `employee` |
| `notification` | Email/SMS dispatch for leave events (submitted/approved/rejected/reminder) | `leave`, `approval`, `employee` (read-only, via events) |
| `common` | Cross-cutting: `TenantContext`, error types, base entities, audit fields, pagination DTOs | none — everything may depend on `common` |

Rules (enforced by Spring Modulith's module verification, not just convention):

- Each row above is one of Spring Modulith's *application modules* — a direct subpackage of the base package `com.bsolz.lms`. Modulith derives the module list from the package structure itself; there is no separate registry to keep in sync.
- A module may only reference another module's `api` subpackage (or classes sitting directly in that module's root package, which Modulith treats as its named interface). Anything under `<module>.internal` is invisible to other modules — Modulith's `ApplicationModules.of(LmsApplication.class).verify()` fails the build if any module reaches into another module's `internal` package. This test lives under `src/test/java` and must run in CI on every PR.
- `notification` never calls into other modules' services synchronously — it only reacts to domain events (e.g. `LeaveRequestApprovedEvent`) published via Spring's `ApplicationEventPublisher`, so a notification failure can never block a leave transaction. Modulith's event publication registry (`spring-modulith-events-*`, not yet added — see `07-open-questions.md`) is the candidate for making that delivery durable across restarts.
- No module may depend on `leave.internal` or `approval.internal` from a controller in another module — if two modules need the same read, add a method to the owning module's `api` service interface.

## Package layout

```
com.bsolz.lms
├── common
│   ├── tenant/            # TenantContext (ThreadLocal), TenantSchemaResolver interface
│   ├── error/             # exception hierarchy, @ControllerAdvice
│   └── audit/             # base entity with created_at/created_by/updated_at/updated_by
├── tenant
│   ├── api/               # TenantService interface, TenantDto
│   ├── internal/          # TenantEntity, TenantRepository, TenantLifecycleService impl
│   └── web/               # TenantAdminController (control-plane, super-admin only)
├── identity
│   ├── api/
│   ├── internal/          # CognitoClaimsMapper, UserEntity, UserRepository
│   └── web/
├── employee
│   ├── api/
│   ├── internal/
│   └── web/
├── leave
│   ├── api/                # LeaveBalanceService, LeaveRequestService interfaces
│   ├── internal/           # LeaveLedgerEntity, LeaveRequestEntity, ledger append logic
│   └── web/
├── approval
│   ├── api/
│   ├── internal/
│   └── web/
└── notification
    ├── api/
    ├── internal/           # listeners on domain events
    └── web/                # (none typically — internal/async only)
```

This is one Maven module (single `pom.xml`, not a multi-module reactor build) — Java's compiler doesn't reject cross-module access on its own, since `internal` is a plain package name, not a language-level visibility boundary. The Spring Modulith verification test above is what actually catches a violation, at test time rather than compile time. Keep classes in `internal` package-private wherever the class doesn't need public visibility for Spring/JPA proxying, as a second line of defense.

## Liquibase layout

Two independent changelog trees, because they migrate on different triggers (public schema on app deploy; a tenant schema on both app deploy *and* new-tenant provisioning):

```
db/changelog/
├── public/
│   ├── db.changelog-master.xml         # includes below, in order
│   ├── modules/
│   │   ├── tenant/                     # tenant registry table, plan metadata
│   │   └── identity/                   # cross-tenant super-admin users, if any
│   └── ...
└── tenant/
    ├── db.changelog-master.xml         # the template applied to every tenant schema
    ├── modules/
    │   ├── employee/
    │   ├── leave/
    │   ├── approval/
    │   └── notification/
    └── ...
```

Conventions:

- One changeset file per logical change, named `NNN-description.xml` (or `.yaml`), included from the module's own `changelog-<module>.xml`, which is in turn included from the tree's master changelog. Ordering is explicit via `<include>`, never relies on filesystem globbing.
- Every changeset has a globally unique `id` and `author`, and a `<rollback>` for anything that isn't purely additive.
- The `tenant/` tree is applied to a newly provisioned tenant schema in full (bootstrapping a new tenant), and to all *existing* tenant schemas when the app deploys with new tenant-tree changesets (see the tenant provisioning/migration job in `06-api-jobs-deployment.md`). The `tenant.migration_status` table (public schema) tracks which changeset count each tenant schema is currently at, so a partially-migrated tenant can be resumed rather than silently skipped.
- No module's changelog references another module's tables directly in a foreign key across module boundaries where avoidable; where a real FK is needed (e.g. `leave_requests.employee_id -> employee.id`), it's declared in the *dependent* module's changelog (`leave`), never edited from `employee`'s.
