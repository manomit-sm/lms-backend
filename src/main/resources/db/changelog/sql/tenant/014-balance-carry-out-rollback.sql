ALTER TABLE leave_balance DROP CONSTRAINT ck_leave_balance_buckets;
ALTER TABLE leave_balance DROP COLUMN available;
ALTER TABLE leave_balance DROP COLUMN carried_out;
ALTER TABLE leave_balance ADD COLUMN available NUMERIC(7, 2) GENERATED ALWAYS AS
    (allocated + carried_forward + adjusted - expired - used - pending) STORED;
ALTER TABLE leave_balance ADD CONSTRAINT ck_leave_balance_buckets
    CHECK (allocated >= 0 AND carried_forward >= 0 AND expired >= 0 AND used >= 0 AND pending >= 0);
