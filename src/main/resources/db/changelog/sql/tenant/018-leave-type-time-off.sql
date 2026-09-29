-- time_off: whether taking the leave type means being away from work. Calendars and availability show
-- employees on a type that isn't time off (work from home) as available.

ALTER TABLE leave_type ADD COLUMN time_off BOOLEAN NOT NULL DEFAULT true;

UPDATE leave_type SET time_off = false WHERE code = 'WFH';
