-- Identity: tenant users (mapped to identity-provider subjects), roles and permissions.
-- Authentication lives in the identity provider (Cognito); nothing here stores credentials.
--
-- idp_subject is null until the identity provider has created the user. employee_id links a user
-- to their employee record; users without one (e.g. the first tenant admin) are allowed.
-- The permission catalog is seeded by Liquibase (004) and grows with releases; it is not editable
-- at runtime. Roles are: system roles (seeded, read-only) plus tenant-defined custom roles.

CREATE TABLE permission
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(64)  NOT NULL,
    module      VARCHAR(40)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    CONSTRAINT uk_permission_code UNIQUE (code)
);

CREATE TABLE role
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code               VARCHAR(40)  NOT NULL,
    name               VARCHAR(100) NOT NULL,
    description        VARCHAR(255),
    system_role        BOOLEAN      NOT NULL DEFAULT false,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_role_code UNIQUE (code)
);

CREATE TABLE role_permission
(
    role_id       UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permission (id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE app_user
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email              VARCHAR(254) NOT NULL,
    idp_subject        VARCHAR(255),
    status             VARCHAR(32)  NOT NULL,
    employee_id        UUID REFERENCES employee (id),
    activated_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         UUID,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_app_user_idp_subject UNIQUE (idp_subject),
    CONSTRAINT uk_app_user_employee UNIQUE (employee_id)
);

CREATE UNIQUE INDEX uk_app_user_email ON app_user (lower(email));

CREATE TABLE user_role
(
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES role (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_role_role ON user_role (role_id);
