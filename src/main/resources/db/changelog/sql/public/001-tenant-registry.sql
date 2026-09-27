-- Tenant registry (control plane). One row per customer organisation; that tenant's business
-- data lives in its own schema (schema_name, e.g. tenant_acme), never in public.
--
-- status is the tenant lifecycle (PROVISIONING -> ACTIVE <-> SUSPENDED -> DEACTIVATED);
-- migration_state tracks whether the tenant schema is on the current changelog
-- (PENDING / UP_TO_DATE / FAILED). They are separate so a failed upgrade never overwrites a
-- suspension, and a tenant only serves requests when ACTIVE and UP_TO_DATE.
--
-- Status columns store the Java enum name as text rather than a Postgres ENUM type, so adding
-- a value later is an application deploy, not a migration that alters a type.

CREATE TABLE tenant
(
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_key       VARCHAR(40)  NOT NULL,
    name             VARCHAR(200) NOT NULL,
    schema_name      VARCHAR(63)  NOT NULL,
    subdomain        VARCHAR(63),
    status           VARCHAR(32)  NOT NULL,
    migration_state  VARCHAR(32)  NOT NULL,
    migrated_at      TIMESTAMPTZ,
    migration_error  TEXT,
    default_timezone VARCHAR(64)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_tenant_tenant_key UNIQUE (tenant_key),
    CONSTRAINT uk_tenant_schema_name UNIQUE (schema_name),
    CONSTRAINT uk_tenant_subdomain UNIQUE (subdomain)
);

CREATE INDEX idx_tenant_status_migration_state ON tenant (status, migration_state);
