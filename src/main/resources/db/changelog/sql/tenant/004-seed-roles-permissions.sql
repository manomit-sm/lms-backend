-- Permission catalog and the four system roles. Codes must match
-- com.bsolz.lms.shared.security.Permissions / SystemRoles (verified by an integration test).
-- New permissions are added in later changesets together with the code that checks them.

INSERT INTO permission (code, module, description)
VALUES ('ORGANIZATION_MANAGE', 'organization', 'Manage departments, designations, locations and work schedules'),
       ('EMPLOYEE_VIEW_ALL', 'organization', 'View every employee in the organisation'),
       ('EMPLOYEE_VIEW_TEAM', 'organization', 'View employees in own reporting line'),
       ('EMPLOYEE_MANAGE', 'organization', 'Create, update and exit employees'),
       ('USER_MANAGE', 'identity', 'Invite, enable and disable users and assign their roles'),
       ('ROLE_MANAGE', 'identity', 'Create and edit custom roles'),
       ('LEAVE_APPLY', 'leave', 'Apply for, view and cancel own leave'),
       ('LEAVE_APPROVE', 'approval', 'Approve or reject leave requests assigned to me'),
       ('LEAVE_MANAGE', 'leave', 'View and act on any leave request'),
       ('LEAVE_POLICY_MANAGE', 'leavepolicy', 'Manage leave types, policies and leave periods'),
       ('BALANCE_ADJUST', 'balance', 'Allocate and adjust leave balances'),
       ('HOLIDAY_MANAGE', 'holiday', 'Manage holidays'),
       ('WORKFLOW_MANAGE', 'approval', 'Manage approval workflows'),
       ('REPORT_VIEW_TEAM', 'reporting', 'View reports for own reporting line'),
       ('REPORT_VIEW_ALL', 'reporting', 'View reports for the whole organisation'),
       ('REPORT_EXPORT', 'reporting', 'Export reports'),
       ('SETTINGS_MANAGE', 'settings', 'Manage tenant settings'),
       ('AUDIT_VIEW', 'audit', 'View the audit log');

INSERT INTO role (code, name, description, system_role)
VALUES ('TENANT_ADMIN', 'Tenant Admin', 'Full access, including roles and settings', true),
       ('HR_ADMIN', 'HR Admin', 'Manages employees, leave, holidays and reports', true),
       ('MANAGER', 'Manager', 'Approves leave and sees their reporting line', true),
       ('EMPLOYEE', 'Employee', 'Applies for and tracks own leave', true);

-- TENANT_ADMIN: everything
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p WHERE r.code = 'TENANT_ADMIN';

-- HR_ADMIN: everything except role and settings management
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.code = 'HR_ADMIN' AND p.code NOT IN ('ROLE_MANAGE', 'SETTINGS_MANAGE');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.code = 'MANAGER' AND p.code IN ('LEAVE_APPLY', 'LEAVE_APPROVE', 'EMPLOYEE_VIEW_TEAM', 'REPORT_VIEW_TEAM');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.code = 'EMPLOYEE' AND p.code IN ('LEAVE_APPLY');
