-- Spring Modulith event publication registry (transactional outbox) for this tenant.
-- Every tenant schema gets its own copy so an event published while a tenant is bound is
-- stored - and later resubmitted - within that tenant's schema, never mixed with others.
--
-- Column layout copied from Spring Modulith 2.1's own v2 Postgres schema
-- (spring-modulith-events-jdbc: schemas/v2/schema-postgresql.sql).

CREATE TABLE event_publication
(
    id                     UUID                     NOT NULL PRIMARY KEY,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE
);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
