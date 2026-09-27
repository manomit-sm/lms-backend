-- Organization structure: work schedules, locations, departments, designations and employees.
-- Unqualified names: applied once per tenant schema with search_path set to that schema.
--
-- Status/type columns store the Java enum name as text (not a Postgres ENUM) so adding a value
-- is an application deploy, not a migration. Emails are unique case-insensitively.

CREATE TABLE work_schedule
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(100) NOT NULL,
    monday             BOOLEAN      NOT NULL,
    tuesday            BOOLEAN      NOT NULL,
    wednesday          BOOLEAN      NOT NULL,
    thursday           BOOLEAN      NOT NULL,
    friday             BOOLEAN      NOT NULL,
    saturday           BOOLEAN      NOT NULL,
    sunday             BOOLEAN      NOT NULL,
    default_schedule   BOOLEAN      NOT NULL DEFAULT false,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_work_schedule_name UNIQUE (name),
    CONSTRAINT ck_work_schedule_has_working_day
        CHECK (monday OR tuesday OR wednesday OR thursday OR friday OR saturday OR sunday)
);

-- At most one default schedule (used when neither the employee nor their location has one).
CREATE UNIQUE INDEX uk_work_schedule_single_default ON work_schedule (default_schedule) WHERE default_schedule;

CREATE TABLE location
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code               VARCHAR(40)  NOT NULL,
    name               VARCHAR(150) NOT NULL,
    country_code       VARCHAR(2)   NOT NULL,
    timezone           VARCHAR(64)  NOT NULL,
    work_schedule_id   UUID REFERENCES work_schedule (id),
    active             BOOLEAN      NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_location_code UNIQUE (code)
);

CREATE TABLE department
(
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                 VARCHAR(40)  NOT NULL,
    name                 VARCHAR(150) NOT NULL,
    parent_department_id UUID REFERENCES department (id),
    head_employee_id     UUID, -- FK added below, once employee exists
    active               BOOLEAN      NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_department_code UNIQUE (code),
    CONSTRAINT ck_department_not_own_parent CHECK (parent_department_id IS NULL OR parent_department_id <> id)
);

CREATE TABLE designation
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(150) NOT NULL,
    level              INT,
    active             BOOLEAN      NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_designation_name UNIQUE (name)
);

CREATE TABLE employee
(
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_code        VARCHAR(40)  NOT NULL,
    first_name           VARCHAR(100) NOT NULL,
    last_name            VARCHAR(100) NOT NULL,
    email                VARCHAR(254) NOT NULL,
    phone                VARCHAR(40),
    gender               VARCHAR(20),
    department_id        UUID         NOT NULL REFERENCES department (id),
    designation_id       UUID REFERENCES designation (id),
    location_id          UUID REFERENCES location (id),
    reporting_manager_id UUID REFERENCES employee (id),
    work_schedule_id     UUID REFERENCES work_schedule (id),
    employment_type      VARCHAR(32)  NOT NULL,
    employment_status    VARCHAR(32)  NOT NULL,
    joining_date         DATE         NOT NULL,
    probation_end_date   DATE,
    exit_date            DATE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_employee_employee_code UNIQUE (employee_code),
    CONSTRAINT ck_employee_not_own_manager CHECK (reporting_manager_id IS NULL OR reporting_manager_id <> id),
    CONSTRAINT ck_employee_exit_after_joining CHECK (exit_date IS NULL OR exit_date >= joining_date)
);

CREATE UNIQUE INDEX uk_employee_email ON employee (lower(email));
CREATE INDEX idx_employee_reporting_manager ON employee (reporting_manager_id);
CREATE INDEX idx_employee_department ON employee (department_id);
CREATE INDEX idx_employee_employment_status ON employee (employment_status);

ALTER TABLE department
    ADD CONSTRAINT fk_department_head_employee FOREIGN KEY (head_employee_id) REFERENCES employee (id);
