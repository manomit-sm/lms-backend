-- Year-end rollover moves unused days out of the ended period (carried_out) and into the next one
-- (carried_forward), so each period's balance still adds up and its ledger explains where the days went.
-- A generated column's expression can't be altered, so "available" is dropped and added again.

ALTER TABLE leave_balance ADD COLUMN carried_out NUMERIC(7, 2) NOT NULL DEFAULT 0;

ALTER TABLE leave_balance DROP COLUMN available;
ALTER TABLE leave_balance ADD COLUMN available NUMERIC(7, 2) GENERATED ALWAYS AS
    (allocated + carried_forward + adjusted - expired - carried_out - used - pending) STORED;

ALTER TABLE leave_balance DROP CONSTRAINT ck_leave_balance_buckets;
ALTER TABLE leave_balance ADD CONSTRAINT ck_leave_balance_buckets
    CHECK (allocated >= 0 AND carried_forward >= 0 AND expired >= 0 AND carried_out >= 0 AND used >= 0
        AND pending >= 0);
