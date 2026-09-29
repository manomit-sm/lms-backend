-- Report exports, generated asynchronously into object storage. scope_* snapshot what the requester
-- could see when they asked, because generation runs later without their session.

CREATE TABLE report_export
(
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requested_by_user_id      UUID         NOT NULL REFERENCES app_user (id),
    report                    VARCHAR(40)  NOT NULL,
    format                    VARCHAR(10)  NOT NULL, -- CSV | XLSX
    parameters                JSONB        NOT NULL,
    scope_tenant_wide         BOOLEAN      NOT NULL,
    scope_manager_employee_id UUID,                  -- the reporting line's head when not tenant-wide
    status                    VARCHAR(20)  NOT NULL, -- PENDING | RUNNING | COMPLETED | FAILED
    file_name                 VARCHAR(255),
    storage_key               VARCHAR(500),
    row_count                 INT,
    size_bytes                BIGINT,
    error                     VARCHAR(1000),
    completed_at              TIMESTAMPTZ,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                UUID,
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by                UUID,
    version                   BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_report_export_scope CHECK (scope_tenant_wide OR scope_manager_employee_id IS NOT NULL)
);

CREATE INDEX idx_report_export_requester ON report_export (requested_by_user_id, created_at DESC);

-- Reports aggregate leave by day.
CREATE INDEX idx_leave_request_day_date ON leave_request_day (leave_date);
