-- Tenant settings: a single row, created here, only ever updated.

CREATE TABLE system_setting
(
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    leave_year_start_month INT          NOT NULL DEFAULT 1,
    timezone               VARCHAR(64), -- null: the tenant's default timezone (public.tenant.default_timezone)
    date_format            VARCHAR(20)  NOT NULL DEFAULT 'dd/MM/yyyy',
    week_start_day         VARCHAR(10)  NOT NULL DEFAULT 'MONDAY',
    organization_name      VARCHAR(150),
    logo_url               VARCHAR(500),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             UUID,
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by             UUID,
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_system_setting_leave_year_start_month CHECK (leave_year_start_month BETWEEN 1 AND 12)
);

-- Exactly one row per tenant schema.
CREATE UNIQUE INDEX uk_system_setting_single_row ON system_setting ((true));

INSERT INTO system_setting DEFAULT VALUES;
