# 01 — Architecture Decisions

Foundational decisions for lms-backend and why we made them. Treat this as an ADR log: each decision below can be revisited, but only by adding a new dated entry that supersedes it, not by silently drifting.

## Decision 1: Modular monolith, not microservices

**We will build a single deployable Spring Boot application, internally organized into strict modules** (`identity`, `tenant`, `employee`, `leave`, `approval`, `notification`, `common`), rather than separate services per module.

**Why:**
- The domain (leave apply/approve, balances, employee records) is small enough that network-boundary overhead between "services" would be pure cost with no benefit — most operations need employee + leave + approval data in one transaction.
- A modular monolith gives us transactional consistency for free (e.g. "create leave request + write ledger entry + notify approver" is one DB transaction, not a saga).
- We keep the *option* to extract a module into its own service later, because module boundaries are enforced in code (see `02-modules.md`) — extraction becomes a deployment change, not a rewrite.
- Team size at this stage doesn't justify the operational overhead of running/monitoring/deploying N services.

**Trade-off accepted:** we give up independent scaling and independent deployment of modules. Revisit if one module (most likely `notification` or `reporting`) develops genuinely different scaling characteristics.

## Decision 2: Multi-tenant SaaS, schema-per-tenant

**Each customer organization ("tenant") gets its own PostgreSQL schema** inside a shared database cluster, plus a small set of shared "public" schema tables for cross-tenant control-plane data (tenant registry, plan/billing metadata, global admin users).

**Why not shared-schema-with-tenant_id-column:**
- Strong isolation: a bug in a `WHERE` clause can leak another tenant's rows in the shared-schema model. In schema-per-tenant, the failure mode is "wrong schema selected" (caught by the checklist in `04-tenancy-and-security.md`), not "missing a filter in one of hundreds of queries."
- Per-tenant backup/restore and per-tenant data export (a real customer ask for HR data) are trivial with schema-per-tenant, painful with a shared table.
- Liquibase can run tenant migrations schema-by-schema, which also gives us a natural per-tenant migration status table (see `02-modules.md`).

**Why not database-per-tenant:**
- Connection pool and operational overhead scale linearly with tenant count; schema-per-tenant lets one connection pool serve all tenants (see the schema-switching mechanism in `04-tenancy-and-security.md`).

**Trade-off accepted:** schema-per-tenant caps us at a few thousand tenants per DB cluster before we'd need sharding across clusters. That's an acceptable ceiling for the foreseeable customer base; revisit in `07-open-questions.md` if we approach it.

## Decision 3: AWS Cognito for authentication

**We use a single Cognito User Pool (per environment) for all tenants' users**, not one pool per tenant, distinguishing tenants via a custom JWT claim.

**Why:**
- Managed MFA, password policies, and token refresh without us owning that code.
- Custom attributes (`custom:tenant_id`, `custom:role`) plus a Pre-Token Generation Lambda trigger let us inject exactly the claims our request pipeline needs (see `04-tenancy-and-security.md`) without maintaining a second identity store.
- One pool per environment (not per tenant) keeps Cognito configuration (app clients, triggers, domain) manageable — tenant separation happens via claims and schema routing, not via infrastructure count.

**Trade-off accepted:** cross-tenant user migration (an employee moving between tenant orgs) requires an explicit admin action to update the `custom:tenant_id` attribute; it's not automatic. Acceptable — this is a rare operation.

## Decision 4: Ledger-based leave balances, not a mutable balance column

**Leave balance is never stored as a single mutable number.** It is the sum of an append-only `leave_ledger` table (accruals, carry-forwards, approved-leave debits, manual adjustments), optionally materialized into a snapshot for read performance.

**Why:**
- Auditability: HR and finance need to answer "why does this employee have 12.5 days" months later. A mutable column can't answer that; a ledger can be replayed.
- Correctness: balance bugs in leave systems are almost always races or double-decrements on a mutable column under concurrent approval. An append-only ledger with a unique constraint per (request, entry type) makes double-application structurally impossible.
- Cheap correction: adjustments are new rows, never edits, so there's no need for soft-delete/undo logic on balance mutations.

**Trade-off accepted:** reading "current balance" is a `SUM` (or a maintained materialized snapshot) instead of a single-row read. See `03-database.md` and `05-leave-flows.md` for the concrete schema and how/when the snapshot is refreshed.

## Decision 5: Liquibase for all schema management

Every schema — public and each tenant schema — is managed exclusively through Liquibase changelogs checked into this repo. No manual DDL against any environment. See `02-modules.md` for changelog layout.

**Why:** repeatable, reviewable, rollback-capable migrations across an unbounded number of tenant schemas is only tractable if it's fully automated and declarative.
