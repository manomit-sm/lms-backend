-- Spring Modulith event publication registry (transactional outbox) for events published
-- outside any tenant context, e.g. by the platform module. Tenant-scoped events are stored in
-- each tenant schema's own copy (sql/tenant/001-event-publication.sql).
--
-- Column layout copied from Spring Modulith 2.1's own v2 Postgres schema
-- (spring-modulith-events-jdbc: schemas/v2/schema-postgresql.sql), which the JPA registry
-- (spring-modulith-starter-jpa) maps as well. Modulith's own schema initialisation is not used:
-- Liquibase owns every table.

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
