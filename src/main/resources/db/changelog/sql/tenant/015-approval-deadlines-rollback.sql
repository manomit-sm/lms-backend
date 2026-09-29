DROP INDEX idx_approval_task_pending;
ALTER TABLE approval_task DROP COLUMN activated_at, DROP COLUMN last_reminded_at, DROP COLUMN escalated_at;
ALTER TABLE approval_request
    DROP COLUMN reminder_after_hours, DROP COLUMN escalate_after_hours, DROP COLUMN auto_approve_after_hours;
ALTER TABLE approval_workflow
    DROP CONSTRAINT ck_approval_workflow_deadlines,
    DROP COLUMN reminder_after_hours, DROP COLUMN escalate_after_hours, DROP COLUMN auto_approve_after_hours;
