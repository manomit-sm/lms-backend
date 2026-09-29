-- Holidays. A holiday applies to everyone unless it lists locations and/or departments; then an
-- employee must match each list that is present.

CREATE TABLE holiday
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(150) NOT NULL,
    holiday_date       DATE         NOT NULL,
    type               VARCHAR(20)  NOT NULL, -- PUBLIC | COMPANY | OPTIONAL (optional: not a day off unless taken)
    description        VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_holiday_date_name ON holiday (holiday_date, lower(name));

CREATE TABLE holiday_location
(
    holiday_id  UUID NOT NULL REFERENCES holiday (id) ON DELETE CASCADE,
    location_id UUID NOT NULL REFERENCES location (id),
    PRIMARY KEY (holiday_id, location_id)
);

CREATE TABLE holiday_department
(
    holiday_id    UUID NOT NULL REFERENCES holiday (id) ON DELETE CASCADE,
    department_id UUID NOT NULL REFERENCES department (id),
    PRIMARY KEY (holiday_id, department_id)
);
