-- The audit trail: one row per business event, written by the audit module's event listeners.
-- Append-only. Names are snapshots taken when the event happened; ids are deliberately not foreign
-- keys, so the log never blocks or is changed by what happens to the rows it mentions.
-- event_key identifies the event, so a redelivered event is recorded once.

CREATE TABLE activity_log
(
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seq           BIGINT GENERATED ALWAYS AS IDENTITY,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    actor_user_id UUID,         -- null: the system (a job, or an automatic decision)
    actor_name    VARCHAR(200),
    action        VARCHAR(60)  NOT NULL,
    entity_type   VARCHAR(40)  NOT NULL,
    entity_id     UUID,
    employee_id   UUID,         -- the employee it concerns, if any: scopes who sees it in activity feeds
    employee_name VARCHAR(200),
    summary       VARCHAR(500) NOT NULL,
    details       JSONB,
    event_key     VARCHAR(200) NOT NULL,
    recorded_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_activity_log_seq UNIQUE (seq),
    CONSTRAINT uk_activity_log_event UNIQUE (event_key)
);

CREATE INDEX idx_activity_log_occurred ON activity_log (occurred_at DESC, seq DESC);
CREATE INDEX idx_activity_log_entity ON activity_log (entity_id);
CREATE INDEX idx_activity_log_employee ON activity_log (employee_id, occurred_at DESC);
CREATE INDEX idx_activity_log_actor ON activity_log (actor_user_id, occurred_at DESC);

CREATE FUNCTION reject_activity_log_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'activity_log is append-only';
END;
$$;

CREATE TRIGGER trg_activity_log_append_only
    BEFORE UPDATE OR DELETE ON activity_log
    FOR EACH ROW EXECUTE FUNCTION reject_activity_log_change();
