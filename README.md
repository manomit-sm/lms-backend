# LMS Backend

Backend of a multi-tenant **Leave Management System**: organisations (tenants) manage employees, leave
policies, holidays and balances; employees apply for leave; managers and HR approve it through
configurable workflows; everyone gets notifications, calendars, dashboards and reports.

It is a REST API for a separate frontend, built as a **modular monolith** (Spring Modulith) on
**Java 25 / Spring Boot 4** and **PostgreSQL**, with **one database schema per tenant**. Authentication
is delegated to AWS Cognito; for local development the backend issues its own tokens with the same
claims, so nothing in AWS is needed to run it.

## What it does

| Area | Features |
|---|---|
| Tenants | Provisioning, suspension, per-tenant schema migrations (platform API) |
| Organisation | Departments, designations, locations, work schedules, employees, reporting hierarchy |
| Users & access | Users linked to employees, system and custom roles, permissions, data scope (self / team / everyone) |
| Leave policy | Leave types, policies with applicability rules (department, location, gender, ...), leave periods |
| Holidays | Holiday calendar per location/department, CSV import |
| Balances | Append-only ledger; allocation, proration, monthly/quarterly accrual, carry forward, expiry, HR adjustments |
| Leave requests | Preview, submit, withdraw, cancel; day calculation (work schedule, holidays, half days, sandwich rule); notice, probation and attachment rules; overlap protection |
| Approvals | Configurable multi-step workflows (manager, skip-level, role, named employee); reminders, escalation, auto-approval |
| Notifications | In-app notifications with a live server-sent event stream; upcoming-leave reminders |
| Calendar | Personal, team and department calendars; availability for a day |
| Audit | Activity log of every business event; recent-activity feed |
| Reporting | Role-aware dashboard, reports (monthly statistics, leave history, department and leave-type usage, decisions, balances), CSV/XLSX exports |

## Architecture in brief

- **Modules** (`com.bsolz.lms.<module>`): `organization`, `settings`, `identity`, `platform`, `leavepolicy`,
  `holiday`, `balance`, `approval`, `leave`, `calendar`, `notification`, `audit`, `reporting`, plus the
  shared kernel `shared` (tenancy, security, errors, storage). Modules only use each other's `api`
  packages; `ModularityTests` enforces this on every build.
- **Tenancy**: the tenant comes only from the access token's `tenant_id` claim and selects the
  PostgreSQL schema (`tenant_<key>`). The `public` schema holds only the tenant registry and job locks.
- **Side effects** (notifications, audit) run asynchronously from domain events stored in a per-tenant
  outbox, so they can never roll back a business change.
- **Migrations**: Liquibase (YAML changelogs, plain SQL files) for `public` and every tenant schema.
- **Errors**: every error is `application/problem+json` with a stable `errorCode`.

More detail: [`docs/architecture/01-decisions.md`](docs/architecture/01-decisions.md) (why) and
[`docs/architecture/02-modules.md`](docs/architecture/02-modules.md) (modules, rules, package and
Liquibase layout).

## Prerequisites

- **JDK 25**
- **PostgreSQL 17 or newer** (the `btree_gist` extension, part of the standard distribution, is
  installed automatically)
- **Docker** - only needed to run the tests (Testcontainers starts its own PostgreSQL)

Maven comes with the project (`./mvnw`). The `curl` examples below use `jq` to extract tokens.

## Running locally

### 1. Database

Use a local PostgreSQL, or start one in Docker:

```bash
docker run -d --name lms-postgres -p 5432:5432 \
  -e POSTGRES_DB=lms -e POSTGRES_USER=lms -e POSTGRES_PASSWORD=lms postgres:17
```

With your own PostgreSQL, create the database and a user that owns it:

```sql
CREATE USER lms WITH PASSWORD 'lms';
CREATE DATABASE lms OWNER lms;
```

> If PostgreSQL is already installed on your machine (e.g. with Homebrew), it usually listens on
> `localhost:5432` too, and the application connects to it rather than to the container. Use one or
> the other, or map the container to another port (`-p 5433:5432`) and use that port in `DB_URL`.

### 2. Start the application

```bash
export DB_URL=jdbc:postgresql://localhost:5432/lms
export DB_USERNAME=lms
export DB_PASSWORD=lms
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

In IntelliJ, run `LmsApplication` with the active profile `local` and the three `DB_*` environment
variables.

On startup the application migrates the `public` schema and every tenant schema, then listens on
<http://localhost:8080>. Check it with <http://localhost:8080/actuator/health>.

The `local` profile:

- issues its own signed tokens (`/local/tokens/**`) instead of using Cognito;
- uses an in-memory identity provider: invited users are only logged, with their token subject;
- uses in-memory file storage: presigned upload/download URLs point nowhere and every presigned upload
  counts as done;
- allows the frontend dev servers `http://localhost:5173` and `http://localhost:3000` (CORS).

### 3. Get tokens and call the API

Everything below also works from Postman or the frontend. Tokens are sent as
`Authorization: Bearer <token>`.

**Create a tenant** with a platform-admin token:

```bash
PLATFORM_TOKEN=$(curl -s -X POST localhost:8080/local/tokens/platform \
  -H 'Content-Type: application/json' \
  -d '{"subject": "platform-admin", "ttlMinutes": 480}' | jq -r .accessToken)

curl -s -X POST localhost:8080/platform/tenants \
  -H "Authorization: Bearer $PLATFORM_TOKEN" -H 'Content-Type: application/json' \
  -d '{"key": "acme", "name": "Acme Corp", "defaultTimezone": "Asia/Kolkata", "adminEmail": "admin@acme.test"}'
```

The response's `id` is the tenant id. Provisioning creates the schema `tenant_acme`, seeds roles,
leave types, default policies, approval workflows and the current leave period, and invites
`admin@acme.test` as tenant admin.

**Sign in as a tenant user.** A tenant token needs the user's subject (their identity-provider id) and
the tenant id. The in-memory identity provider logs every invited user's subject:

```
Fake identity provider: invited admin@acme.test (sub=f0fcf463-d075-455e-bdbe-21ba2b69d892)
```

The subject is also stored in the tenant schema:

```sql
SELECT email, idp_subject FROM tenant_acme.app_user;
```

```bash
TOKEN=$(curl -s -X POST localhost:8080/local/tokens/tenant \
  -H 'Content-Type: application/json' \
  -d '{"subject": "<subject>", "tenantId": "<tenant id>", "ttlMinutes": 480}' | jq -r .accessToken)

curl -s localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN"
```

`/api/v1/auth/me` returns the user, their roles and permissions, which is what the frontend uses to
decide what to show. Employees created through `POST /api/v1/employees` are invited the same way: take
their subject from the log or `app_user` to sign in as them.

A typical first session as the tenant admin: create departments and employees (with reporting
managers), grant roles (`PUT /api/v1/users/{id}/roles`: `MANAGER`, `HR_ADMIN`), then sign in as an
employee and apply for leave (`POST /api/v1/leave-requests`), and as their manager approve it
(`GET /api/v1/approvals/tasks`, `POST /api/v1/approvals/tasks/{id}/approve`).

### API documentation

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI documents: `/v3/api-docs/tenant` (the tenant API, `/api/**`) and `/v3/api-docs/platform`
  (tenant provisioning, `/platform/**`)

The `ProblemDetail` schema lists every `errorCode` the API returns, with its HTTP status.

### Notes for the frontend

- **Roles**: `TENANT_ADMIN`, `HR_ADMIN`, `MANAGER`, `EMPLOYEE` (every user), plus custom roles. Check
  permissions from `/api/v1/auth/me` rather than role names.
- **Errors**: `application/problem+json` with `errorCode` and a human-readable `detail`;
  `VALIDATION_FAILED` lists field errors in `errors`, broken leave rules are in `violations`.
- **Live notifications**: `GET /api/v1/notifications/stream` (server-sent events) needs the
  `Authorization` header, so use a fetch-based EventSource client; tokens are never accepted in the URL.
- **Uploads and downloads** (leave attachments, report exports) go directly to storage through
  presigned URLs returned by the API.
- **Paging**: `?page=0&size=20` (at most 100); responses are `{content, page, size, totalElements, totalPages}`.

## Tests

```bash
./mvnw clean test
```

Docker must be running: integration tests start PostgreSQL with Testcontainers and share one
application context. They cover:

- **Modules and architecture**: module boundaries (`ModularityTests`) and tenancy rules
  (`ArchitectureRulesTests`).
- **Security**: every endpoint against every role (`SecurityMatrixTests`).
- **Tenant isolation**: requests, listeners and scheduled jobs stay in their own tenant's schema.
- **Features**: the API tests of every module.

## Configuration

| Variable | Needed for | Meaning |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | always | PostgreSQL connection |
| `LMS_TENANT_ISSUER_URI`, `LMS_TENANT_CLIENT_IDS` | deployed | Cognito tenant user pool issuer and accepted app client ids |
| `LMS_PLATFORM_ISSUER_URI`, `LMS_PLATFORM_CLIENT_IDS` | deployed | Cognito platform-admin pool issuer and app client ids |
| `LMS_COGNITO_TENANT_USER_POOL_ID`, `AWS_REGION` | deployed | Inviting and disabling users in the tenant pool |
| `LMS_S3_BUCKET` | deployed | Bucket for attachments and report exports |
| `LMS_CORS_ALLOWED_ORIGINS` | deployed | Frontend origins, comma-separated |

The `local` and `test` profiles set everything except the database. Profiles:

- `local`: development, as above.
- `test`: used by the tests.
- `aws`: deployed environments: JSON logs, no migrations at startup.
- `migrate`: add it to run all migrations and exit.

Scheduled jobs (`lms.jobs.*`, cron in UTC):

- balance maintenance: open the next leave year, accrue, carry forward and expire days;
- approval reminders, escalation and auto-approval;
- upcoming-leave notifications;
- per-tenant event resubmission and cleanup.

The test profile turns them off; the tests run them directly.

## Project layout

```
src/main/java/com/bsolz/lms/
├── LmsApplication.java
├── shared/          # tenancy, security, errors, storage, config (open to every module)
├── platform/        # tenant registry, provisioning, migrations, /platform API
├── identity/        # users, roles, permissions, /auth/me
├── organization/    # departments, locations, designations, work schedules, employees
├── settings/        # tenant settings
├── leavepolicy/     # leave types, policies, leave periods
├── holiday/         # holidays
├── balance/         # balances, ledger, allocation, accrual, year end
├── approval/        # workflows, approvals, deadlines
├── leave/           # leave requests
├── calendar/        # calendars and availability
├── notification/    # notifications and the live stream
├── audit/           # activity log
└── reporting/       # dashboard, reports, exports
src/main/resources/
├── application*.yaml
└── db/changelog/    # Liquibase: db.changelog-public.yaml, db.changelog-master.yaml (tenant), changes/, sql/
```

Each module follows the same layout: `api/` (its public API and events), `web/`, `service/`, `domain/`,
`entity/`, `repository/`, `model/enums/`, `exception/`.
