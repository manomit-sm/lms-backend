-- Default leave types, one default policy per type, and the current leave period (calendar year, as
-- the default leave year starts in January). Tenants edit or replace all of these.

INSERT INTO leave_type (code, name, description, color, paid, balance_tracked, half_day_allowed, sort_order)
VALUES ('ANNUAL', 'Annual Leave', 'Planned time off', '#2563EB', true, true, true, 10),
       ('CASUAL', 'Casual Leave', 'Short-notice personal time off', '#16A34A', true, true, true, 20),
       ('SICK', 'Sick Leave', 'Illness and medical appointments', '#DC2626', true, true, true, 30),
       ('MATERNITY', 'Maternity Leave', 'Leave for birth or adoption', '#DB2777', true, true, false, 40),
       ('PATERNITY', 'Paternity Leave', 'Leave for a new parent', '#7C3AED', true, true, false, 50),
       ('UNPAID', 'Unpaid Leave', 'Time off without pay', '#6B7280', false, false, true, 60),
       ('WFH', 'Work From Home', 'Working remotely; not time off', '#0891B2', true, false, true, 70);

INSERT INTO leave_policy (leave_type_id, name, entitlement_days, accrual_method, prorate_on_joining,
                          carry_forward_max_days, max_consecutive_days, min_notice_days, backdating_allowed_days,
                          attachment_required_after_days, allowed_during_probation)
SELECT t.id, 'Default ' || t.name, v.entitlement_days, 'UPFRONT', v.prorate_on_joining, v.carry_forward_max_days,
       v.max_consecutive_days, v.min_notice_days, v.backdating_allowed_days, v.attachment_required_after_days,
       v.allowed_during_probation
FROM leave_type t
         JOIN (VALUES ('ANNUAL', 18.00, true, 10.00, NULL::int, 7, 0, NULL::numeric, false),
                      ('CASUAL', 8.00, true, 0.00, 3, 1, 0, NULL, true),
                      ('SICK', 10.00, true, 0.00, NULL, 0, 30, 2.00, true),
                      ('MATERNITY', 182.00, false, 0.00, NULL, 30, 0, 0.00, true),
                      ('PATERNITY', 10.00, false, 0.00, NULL, 7, 0, NULL, true),
                      ('UNPAID', 0.00, false, 0.00, NULL, 7, 0, NULL, true),
                      ('WFH', 0.00, false, 0.00, NULL, 1, 7, NULL, true))
    AS v (code, entitlement_days, prorate_on_joining, carry_forward_max_days, max_consecutive_days,
          min_notice_days, backdating_allowed_days, attachment_required_after_days, allowed_during_probation)
              ON v.code = t.code;

-- Policies without rules apply to everyone; maternity is limited to female and paternity to male employees.
INSERT INTO leave_policy_applicability (leave_policy_id, gender)
SELECT p.id, CASE t.code WHEN 'MATERNITY' THEN 'FEMALE' ELSE 'MALE' END
FROM leave_policy p
         JOIN leave_type t ON t.id = p.leave_type_id
WHERE t.code IN ('MATERNITY', 'PATERNITY');

INSERT INTO leave_period (name, start_date, end_date)
SELECT to_char(current_date, 'YYYY'),
       make_date(extract(YEAR FROM current_date)::int, 1, 1),
       make_date(extract(YEAR FROM current_date)::int, 12, 31);
