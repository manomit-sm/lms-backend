-- Leave balances and their ledger. Every change to a balance is a ledger row written in the same
-- transaction, under a row lock on the balance, so each bucket always equals the sum of its ledger rows.

CREATE TABLE leave_balance
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id        UUID          NOT NULL REFERENCES employee (id),
    leave_type_id      UUID          NOT NULL REFERENCES leave_type (id),
    leave_period_id    UUID          NOT NULL REFERENCES leave_period (id),
    allocated          NUMERIC(7, 2) NOT NULL DEFAULT 0, -- allocations and accruals
    carried_forward    NUMERIC(7, 2) NOT NULL DEFAULT 0,
    adjusted           NUMERIC(7, 2) NOT NULL DEFAULT 0, -- net manual adjustments (may be negative)
    expired            NUMERIC(7, 2) NOT NULL DEFAULT 0,
    used               NUMERIC(7, 2) NOT NULL DEFAULT 0, -- approved leave
    pending            NUMERIC(7, 2) NOT NULL DEFAULT 0, -- held by requests awaiting approval
    available          NUMERIC(7, 2) GENERATED ALWAYS AS
                           (allocated + carried_forward + adjusted - expired - used - pending) STORED,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by         UUID,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_leave_balance UNIQUE (employee_id, leave_type_id, leave_period_id),
    CONSTRAINT ck_leave_balance_buckets
        CHECK (allocated >= 0 AND carried_forward >= 0 AND expired >= 0 AND used >= 0 AND pending >= 0)
);

CREATE INDEX idx_leave_balance_period_type ON leave_balance (leave_period_id, leave_type_id);

-- Append-only. amount is positive except for ADJUSTMENT, whose sign is the direction.
CREATE TABLE leave_balance_transaction
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seq                BIGINT GENERATED ALWAYS AS IDENTITY,
    leave_balance_id   UUID          NOT NULL REFERENCES leave_balance (id),
    type               VARCHAR(20)   NOT NULL,
    amount             NUMERIC(7, 2) NOT NULL,
    available_after    NUMERIC(7, 2) NOT NULL,
    reference_type     VARCHAR(40)   NOT NULL,
    reference_id       UUID,
    note               VARCHAR(500),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID,
    CONSTRAINT uk_leave_balance_transaction_seq UNIQUE (seq),
    CONSTRAINT ck_leave_balance_transaction_amount
        CHECK (amount <> 0 AND (type = 'ADJUSTMENT' OR amount > 0))
);

CREATE INDEX idx_leave_balance_transaction_balance ON leave_balance_transaction (leave_balance_id, seq);
CREATE INDEX idx_leave_balance_transaction_reference ON leave_balance_transaction (reference_id)
    WHERE reference_id IS NOT NULL;

CREATE FUNCTION reject_leave_balance_transaction_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'leave_balance_transaction is append-only';
END;
$$;

CREATE TRIGGER trg_leave_balance_transaction_append_only
    BEFORE UPDATE OR DELETE ON leave_balance_transaction
    FOR EACH ROW EXECUTE FUNCTION reject_leave_balance_transaction_change();
