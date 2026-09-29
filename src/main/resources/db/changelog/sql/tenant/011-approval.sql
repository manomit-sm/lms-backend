-- Approval workflows (definitions) and approvals (runtime). An approval snapshots its workflow's steps
-- as tasks, with the resolved approvers, when it starts: later workflow or org changes don't affect it.

-- Workflows are tried by ascending priority; the first whose rules match is used, else the default one.
CREATE TABLE approval_workflow
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(150) NOT NULL,
    description        VARCHAR(500),
    priority           INT          NOT NULL,
    default_workflow   BOOLEAN      NOT NULL DEFAULT false,
    active             BOOLEAN      NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_approval_workflow_name UNIQUE (name),
    CONSTRAINT uk_approval_workflow_priority UNIQUE (priority),
    CONSTRAINT ck_approval_workflow_default_active CHECK (NOT default_workflow OR active)
);

CREATE UNIQUE INDEX uk_approval_workflow_single_default ON approval_workflow (default_workflow) WHERE default_workflow;

-- A workflow matches a request when any of its rules does; a rule matches when every criterion given does.
CREATE TABLE approval_workflow_rule
(
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_workflow_id UUID NOT NULL REFERENCES approval_workflow (id) ON DELETE CASCADE,
    leave_type_id        UUID REFERENCES leave_type (id),
    min_days             NUMERIC(6, 2),
    CONSTRAINT ck_approval_workflow_rule_has_criterion CHECK (num_nonnulls(leave_type_id, min_days) > 0)
);

CREATE INDEX idx_approval_workflow_rule_workflow ON approval_workflow_rule (approval_workflow_id);

-- approver_type: REPORTING_MANAGER | SKIP_LEVEL_MANAGER | ROLE (role_code) | EMPLOYEE (employee_id)
CREATE TABLE approval_step
(
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_workflow_id UUID        NOT NULL REFERENCES approval_workflow (id) ON DELETE CASCADE,
    step_order           INT         NOT NULL,
    approver_type        VARCHAR(30) NOT NULL,
    role_code            VARCHAR(50),
    employee_id          UUID REFERENCES employee (id),
    CONSTRAINT uk_approval_step_order UNIQUE (approval_workflow_id, step_order),
    CONSTRAINT ck_approval_step_role CHECK ((approver_type = 'ROLE') = (role_code IS NOT NULL)),
    CONSTRAINT ck_approval_step_employee CHECK ((approver_type = 'EMPLOYEE') = (employee_id IS NOT NULL))
);

-- subject_type/subject_id: what is being approved (e.g. LEAVE_REQUEST + the leave request id). At most
-- one pending approval per subject.
CREATE TABLE approval_request
(
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type          VARCHAR(40)  NOT NULL,
    subject_id            UUID         NOT NULL,
    requester_employee_id UUID         NOT NULL REFERENCES employee (id),
    requester_user_id     UUID         NOT NULL REFERENCES app_user (id),
    approval_workflow_id  UUID         NOT NULL REFERENCES approval_workflow (id),
    workflow_name         VARCHAR(150) NOT NULL,
    status                VARCHAR(20)  NOT NULL, -- PENDING | APPROVED | REJECTED | CANCELLED
    completed_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            UUID,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by            UUID,
    version               BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_approval_request_pending_subject ON approval_request (subject_type, subject_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_approval_request_subject ON approval_request (subject_id);

-- One task per workflow step. status: WAITING | PENDING | APPROVED | REJECTED | SKIPPED | CANCELLED
CREATE TABLE approval_task
(
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_request_id  UUID         NOT NULL REFERENCES approval_request (id),
    step_order           INT          NOT NULL,
    approver_type        VARCHAR(30)  NOT NULL,
    approver_description VARCHAR(200) NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    acted_by_user_id     UUID REFERENCES app_user (id),
    acted_at             TIMESTAMPTZ,
    comment              VARCHAR(1000),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           UUID,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by           UUID,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_approval_task_step UNIQUE (approval_request_id, step_order)
);

-- Who may act on a task: any one of them decides the step.
CREATE TABLE approval_task_assignee
(
    approval_task_id UUID NOT NULL REFERENCES approval_task (id) ON DELETE CASCADE,
    user_id          UUID NOT NULL REFERENCES app_user (id),
    PRIMARY KEY (approval_task_id, user_id)
);

CREATE INDEX idx_approval_task_assignee_user ON approval_task_assignee (user_id);
