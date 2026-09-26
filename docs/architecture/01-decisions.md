# 01 — Architecture Decisions

Foundational decisions for lms-backend and why we made them. Treat this as an ADR log: each decision below can be revisited, but only by adding a new dated entry that supersedes it, not by silently drifting.

All decisions below: **accepted 2026-09-27**.

## Decision 1: Modular monolith, not microservices

**We build a single deployable Spring Boot application, internally organized into strict Spring Modulith modules** (see `02-modules.md`), rather than separate services per module.

**Why:**
- **Transactional consistency.** Approving a leave request changes the request status, moves days from *pending* to *used* in the balance, advances the approval workflow, writes the audit log and creates a notification. In one application that is one transaction; across services it would need sagas and compensations.
- **Small, tightly coupled domain.** Almost every screen (calendar, dashboard, reports) reads employees, leave requests, holidays and balances together.
- **Low load.** Even large tenants generate little traffic; independent scaling buys nothing.
- **Reports** join across modules — trivial in one database, painful across service databases.
- **Cost and team size.** One pipeline, one database, one deploy.

We keep the *option* to extract a module later: boundaries are enforced in code by Spring Modulith, and side effects flow through domain events, so extraction becomes a deployment change rather than a rewrite.

**Trade-off accepted:** no independent scaling or deployment per module. Revisit if a module (most likely `notification` or `reporting` exports) develops genuinely different scaling characteristics.

## Decision 2: Multi-tenant SaaS

**The codebase is multi-tenant from day one.** The same build supports these deployment models:

| Model | Use |
|---|---|
| **Shared SaaS** — many tenants, one environment | Default offering |
| **Dedicated instance** — same image, one tenant, separate AWS account/region | Premium: isolation, data residency |
| **Client-hosted** | Only if a specific deal justifies it; not designed for, but not made impossible |

**Why:**
- Leave management is a low price-per-employee product. A dedicated environment per small customer costs more to run than the customer pays.
- Running multi-tenant code with a single tenant is free; retrofitting multi-tenancy into single-tenant code is a rewrite (security, every query, jobs, caches, migrations).

**Consequences — to keep dedicated/client-hosted possible:**
- One codebase, one image, no customer-specific branches; per-tenant differences are data and configuration.
- Everything deployment-specific (identity provider issuer, storage bucket, email sender, database) is configuration.
- Cloud integrations sit behind interfaces (`FileStorage`, `EmailSender`, `IdentityProviderClient`).
- Infrastructure as code from the first deployment.

## Decision 3: Schema-per-tenant on PostgreSQL

**Each tenant gets its own PostgreSQL schema** (`t_<slug>`) in a shared database, served by one connection pool. The `public` schema holds only control-plane data (tenant registry, tenant identity-provider mapping, job locks). Business tables have **no `tenant_id` column** — the schema is the boundary.

**Why not a shared schema with a `tenant_id` column:**
- A single missing `WHERE tenant_id = ?` leaks data. With schema-per-tenant the only failure mode is "wrong schema selected", which is handled in one place and covered by dedicated isolation tests.
- Per-tenant export, restore and offboarding (drop schema) are trivial.

**Why not database-per-tenant:**
- Connection pools and operational overhead grow linearly with tenant count.

**How the schema is selected:** the tenant is taken **only** from the validated access token's `tenant_id` claim, looked up in the tenant registry and mapped to a schema. It is never taken from a header, path, query parameter or request body, and the schema name never appears in the token.

**Trade-off accepted:** comfortable up to hundreds or low thousands of tenants per database; migration time grows with tenant count. Beyond that we would shard tenants across databases.

## Decision 4: AWS Cognito for authentication, our database for authorization

**Authentication** (login, MFA, password reset, corporate SSO) is delegated to **one Cognito user pool per environment** shared by all tenants. **Authorization** (roles, permissions, data scope) lives in each tenant's schema.

**Why Cognito:**
- Managed MFA, password policies, token lifecycle and breach protection — we don't own credential code.
- Per-tenant corporate SSO (SAML/OIDC) is configuration, not a project.
- We are on AWS already.

**How the tenant gets into the token:**
- Custom attribute `custom:tenant_id`, **read-only for every app client** (otherwise users could change their own tenant).
- A Pre Token Generation Lambda copies it into the **access token** as a `tenant_id` claim; for SSO users it is derived from the identity provider they signed in through.
- Platform administrators (our staff) use a separate user pool.

**Why authorization stays in our database, not in Cognito groups/attributes:**
- Roles and permissions are tenant-configurable at runtime and depend on the org chart (a manager sees their reporting line).
- Group claims only change when a new token is issued; permission changes in our database apply almost immediately.

**Portability:** the backend is a plain OAuth2 resource server. Switching identity provider (Keycloak, Microsoft Entra, a self-hosted authorization server) is a configuration change.

**Trade-offs accepted:**
- Password hashes can't be exported from Cognito; leaving Cognito would force password resets for non-SSO users.
- One pool with email sign-in means an email address exists once platform-wide, so a person belongs to one tenant.

## Decision 5: Ledger-based leave balances

**Every balance change is an immutable row in an append-only ledger** (`leave_balance_transaction`: allocation, accrual, carry-forward, expiry, pending hold/release, consume, reversal, HR adjustment). A snapshot row per employee × leave type × leave period (`leave_balance`: allocated, carried forward, adjusted, used, pending, and a generated *available* column) is updated **in the same transaction** as the ledger entry.

**Why:**
- **Auditability:** "why does this employee have 12.5 days?" is answered by the ledger.
- **Correctness:** balance bugs are almost always races on concurrent submit/approve. The snapshot row is locked (`SELECT … FOR UPDATE`) while checking and changing it, so two requests cannot both spend the same days.
- **Cheap reads:** dashboards and the apply form read one row, not a `SUM`.
- **Corrections** are new ledger rows, never edits.

**Trade-off accepted:** two writes per change, and the snapshot must only ever be changed through the balance module's API.

## Decision 6: Liquibase owns every schema

Every schema — `public` and each tenant schema — is managed exclusively by **Liquibase YAML changelogs** in this repository. Hibernate never creates or validates schema (`ddl-auto: none`). No manual DDL in any environment.

- Boot's Liquibase auto-run is disabled; a tenant migration runner applies the public changelog, then the tenant changelog to each tenant schema (each schema has its own Liquibase history table).
- New tenants are provisioned by creating the schema and applying the full tenant changelog.
- In production, migrations run as a separate step before the application rolls out.

**Why:** repeatable, reviewable migrations across an unbounded number of tenant schemas are only tractable if they are fully automated and declarative.
