-- Leave requests, their per-day breakdown, status history and attachments.

-- start_session/end_session: FULL_DAY | FIRST_HALF | SECOND_HALF. total_days counts only the days that
-- draw on leave (see leave_request_day). status: PENDING | APPROVED | REJECTED | WITHDRAWN |
-- CANCELLATION_PENDING | CANCELLED.
CREATE TABLE leave_request
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id         UUID          NOT NULL REFERENCES employee (id),
    leave_type_id       UUID          NOT NULL REFERENCES leave_type (id),
    leave_period_id     UUID          NOT NULL REFERENCES leave_period (id),
    start_date          DATE          NOT NULL,
    end_date            DATE          NOT NULL,
    start_session       VARCHAR(20)   NOT NULL,
    end_session         VARCHAR(20)   NOT NULL,
    total_days          NUMERIC(6, 2) NOT NULL,
    reason              VARCHAR(1000),
    status              VARCHAR(30)   NOT NULL,
    decided_at          TIMESTAMPTZ,
    cancellation_reason VARCHAR(1000),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by          UUID,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_leave_request_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_leave_request_total_days CHECK (total_days > 0),
    -- An employee's live requests never overlap (whole days: two half days of one date also conflict).
    CONSTRAINT ex_leave_request_overlap EXCLUDE USING gist (
        employee_id public.gist_uuid_ops WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    ) WHERE (status IN ('PENDING', 'APPROVED', 'CANCELLATION_PENDING'))
);

CREATE INDEX idx_leave_request_employee_dates ON leave_request (employee_id, start_date);
CREATE INDEX idx_leave_request_status ON leave_request (status);

-- One row per calendar day of the request. day_type: WORKING | WEEKEND | HOLIDAY; amount: the leave
-- days it draws (1, 0.5 or 0 - non-working days count only under the sandwich rule).
CREATE TABLE leave_request_day
(
    leave_request_id UUID          NOT NULL REFERENCES leave_request (id) ON DELETE CASCADE,
    leave_date       DATE          NOT NULL,
    day_type         VARCHAR(20)   NOT NULL,
    session          VARCHAR(20)   NOT NULL,
    amount           NUMERIC(3, 1) NOT NULL,
    PRIMARY KEY (leave_request_id, leave_date)
);

-- Every status change, append-only.
CREATE TABLE leave_request_history
(
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    leave_request_id UUID          NOT NULL REFERENCES leave_request (id),
    from_status      VARCHAR(30),
    to_status        VARCHAR(30)   NOT NULL,
    action           VARCHAR(40)   NOT NULL,
    actor_user_id    UUID,
    comment          VARCHAR(1000),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_leave_request_history_request ON leave_request_history (leave_request_id, created_at);

-- Uploaded straight to object storage with a presigned URL; the row exists from the moment the upload
-- is requested and is linked to a request when that request is submitted.
CREATE TABLE leave_attachment
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    leave_request_id    UUID REFERENCES leave_request (id),
    uploaded_by_user_id UUID          NOT NULL REFERENCES app_user (id),
    file_name           VARCHAR(255)  NOT NULL,
    content_type        VARCHAR(100)  NOT NULL,
    size_bytes          BIGINT        NOT NULL,
    storage_key         VARCHAR(500)  NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by          UUID,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_leave_attachment_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_leave_attachment_request ON leave_attachment (leave_request_id);
