-- ShedLock's lock table (https://github.com/lukas-krecan/ShedLock#jdbctemplate), shared by all
-- instances so each scheduled job runs once per cluster. Lives in public because jobs iterate
-- tenants themselves (TenantJobRunner); the lock is per job, not per tenant schema.

CREATE TABLE shedlock
(
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
