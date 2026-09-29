-- In-app notifications, one row per recipient user. dedup_key identifies what caused the notification
-- (e.g. a leave request's status change), so a redelivered event never notifies twice.

CREATE TABLE notification
(
    id           UUID PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES app_user (id),
    type         VARCHAR(40)   NOT NULL,
    title        VARCHAR(200)  NOT NULL,
    message      VARCHAR(1000) NOT NULL,
    subject_type VARCHAR(40),  -- what it is about, for the frontend to link to (e.g. LEAVE_REQUEST)
    subject_id   UUID,
    dedup_key    VARCHAR(200)  NOT NULL,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_notification_dedup UNIQUE (user_id, dedup_key)
);

CREATE INDEX idx_notification_user_created ON notification (user_id, created_at DESC);
CREATE INDEX idx_notification_user_unread ON notification (user_id) WHERE read_at IS NULL;
