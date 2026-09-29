-- Deadlines for pending approval steps, in hours since the step became current (null: never):
-- remind the approvers (repeatedly, every reminder_after_hours), escalate once to the approvers'
-- managers (else HR admins), and approve automatically. Approvals snapshot them from their workflow
-- when they start, like the steps.

ALTER TABLE approval_workflow
    ADD COLUMN reminder_after_hours     INT,
    ADD COLUMN escalate_after_hours     INT,
    ADD COLUMN auto_approve_after_hours INT,
    ADD CONSTRAINT ck_approval_workflow_deadlines
        CHECK ((reminder_after_hours IS NULL OR reminder_after_hours > 0)
            AND (escalate_after_hours IS NULL OR escalate_after_hours > 0)
            AND (auto_approve_after_hours IS NULL OR auto_approve_after_hours > 0));

ALTER TABLE approval_request
    ADD COLUMN reminder_after_hours     INT,
    ADD COLUMN escalate_after_hours     INT,
    ADD COLUMN auto_approve_after_hours INT;

-- activated_at: when the step became current; the deadlines count from here.
ALTER TABLE approval_task
    ADD COLUMN activated_at     TIMESTAMPTZ,
    ADD COLUMN last_reminded_at TIMESTAMPTZ,
    ADD COLUMN escalated_at     TIMESTAMPTZ;

UPDATE approval_task SET activated_at = updated_at WHERE status = 'PENDING';

CREATE INDEX idx_approval_task_pending ON approval_task (activated_at) WHERE status = 'PENDING';

-- The seeded workflows remind after a day and escalate after three; nothing is approved automatically.
UPDATE approval_workflow SET reminder_after_hours = 24, escalate_after_hours = 72
WHERE name IN ('Standard', 'Long leave');
