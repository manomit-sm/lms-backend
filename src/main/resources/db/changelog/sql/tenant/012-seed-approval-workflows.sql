-- Default workflows: the reporting manager approves; five days or more also need HR.

INSERT INTO approval_workflow (name, description, priority, default_workflow)
VALUES ('Standard', 'The reporting manager approves', 1000, true),
       ('Long leave', 'Five days or more: the reporting manager, then HR', 10, false);

INSERT INTO approval_workflow_rule (approval_workflow_id, min_days)
SELECT id, 5 FROM approval_workflow WHERE name = 'Long leave';

INSERT INTO approval_step (approval_workflow_id, step_order, approver_type)
SELECT id, 1, 'REPORTING_MANAGER' FROM approval_workflow;

INSERT INTO approval_step (approval_workflow_id, step_order, approver_type, role_code)
SELECT id, 2, 'ROLE', 'HR_ADMIN' FROM approval_workflow WHERE name = 'Long leave';
