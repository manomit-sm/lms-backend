-- Leave periods, leave types, leave policies and the rules deciding which employees a policy applies to.

-- A leave period (usually a leave year) is what balances are allocated against. Periods never overlap.
CREATE TABLE leave_period
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(100) NOT NULL,
    start_date         DATE         NOT NULL,
    end_date           DATE         NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_leave_period_name UNIQUE (name),
    CONSTRAINT ck_leave_period_dates CHECK (end_date > start_date),
    CONSTRAINT ex_leave_period_overlap EXCLUDE USING gist (daterange(start_date, end_date, '[]') WITH &&)
);

-- paid: counts as paid time. balance_tracked: requests draw on a balance (false for e.g. unpaid leave
-- and work from home, which are never limited by a balance).
CREATE TABLE leave_type
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code               VARCHAR(40)  NOT NULL,
    name               VARCHAR(100) NOT NULL,
    description        VARCHAR(500),
    color              VARCHAR(7)   NOT NULL,
    paid               BOOLEAN      NOT NULL,
    balance_tracked    BOOLEAN      NOT NULL,
    half_day_allowed   BOOLEAN      NOT NULL DEFAULT true,
    active             BOOLEAN      NOT NULL DEFAULT true,
    sort_order         INT          NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_leave_type_code UNIQUE (code),
    CONSTRAINT ck_leave_type_code_upper CHECK (code = upper(code)),
    CONSTRAINT ck_leave_type_color CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE UNIQUE INDEX uk_leave_type_name ON leave_type (lower(name));

-- The rules for one leave type. Several policies per type are allowed (e.g. per location); the most
-- specific one whose applicability matches the employee wins (see leave_policy_applicability).
-- Null effective dates are open-ended.
CREATE TABLE leave_policy
(
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    leave_type_id                  UUID          NOT NULL REFERENCES leave_type (id),
    name                           VARCHAR(150)  NOT NULL,
    description                    VARCHAR(500),
    effective_from                 DATE,
    effective_to                   DATE,
    entitlement_days               NUMERIC(6, 2) NOT NULL,           -- per leave period
    accrual_method                 VARCHAR(20)   NOT NULL,           -- UPFRONT | MONTHLY | QUARTERLY
    prorate_on_joining             BOOLEAN       NOT NULL,
    carry_forward_max_days         NUMERIC(6, 2) NOT NULL DEFAULT 0, -- 0: nothing carries forward
    carry_forward_expiry_months    INT,                              -- null: carried days never expire
    negative_balance_limit         NUMERIC(6, 2) NOT NULL DEFAULT 0, -- how far below zero a balance may go
    max_consecutive_days           INT,                              -- null: no limit
    min_notice_days                INT           NOT NULL DEFAULT 0,
    backdating_allowed_days        INT           NOT NULL DEFAULT 0, -- 0: no backdated requests
    attachment_required_after_days NUMERIC(6, 2),                    -- null: never required
    allowed_during_probation       BOOLEAN       NOT NULL DEFAULT true,
    sandwich_rule                  BOOLEAN       NOT NULL DEFAULT false, -- count weekends/holidays inside a request
    active                         BOOLEAN       NOT NULL DEFAULT true,
    created_at                     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by                     UUID,
    updated_at                     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by                     UUID,
    version                        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_leave_policy_name UNIQUE (name),
    CONSTRAINT ck_leave_policy_effective_dates
        CHECK (effective_from IS NULL OR effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_leave_policy_amounts
        CHECK (entitlement_days >= 0 AND carry_forward_max_days >= 0 AND negative_balance_limit >= 0
            AND (carry_forward_expiry_months IS NULL OR carry_forward_expiry_months > 0)
            AND (max_consecutive_days IS NULL OR max_consecutive_days > 0)
            AND min_notice_days >= 0 AND backdating_allowed_days >= 0
            AND (attachment_required_after_days IS NULL OR attachment_required_after_days >= 0))
);

CREATE INDEX idx_leave_policy_leave_type ON leave_policy (leave_type_id);

-- Who a policy applies to. Each row is a rule naming at least one criterion; a criterion left null
-- matches anyone. A policy applies when any of its rules matches; a policy without rules applies to everyone.
CREATE TABLE leave_policy_applicability
(
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    leave_policy_id  UUID NOT NULL REFERENCES leave_policy (id) ON DELETE CASCADE,
    department_id    UUID REFERENCES department (id),
    designation_id   UUID REFERENCES designation (id),
    location_id      UUID REFERENCES location (id),
    employment_type  VARCHAR(32),
    gender           VARCHAR(20),
    CONSTRAINT ck_leave_policy_applicability_has_criterion
        CHECK (num_nonnulls(department_id, designation_id, location_id, employment_type, gender) > 0)
);

CREATE INDEX idx_leave_policy_applicability_policy ON leave_policy_applicability (leave_policy_id);
