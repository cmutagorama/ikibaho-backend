CREATE TABLE app_user
(
    id              uuid PRIMARY KEY,
    organization_id uuid        NOT NULL,
    email           text        NOT NULL,
    password_hash   text        NOT NULL,
    display_name    text        NOT NULL,
    avatar_url      text,
    status          text        NOT NULL,
    global_role     text        NOT NULL,
    email_verified  boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_app_user_org FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT uq_app_user_org_email UNIQUE (organization_id, email),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'INVITED', 'DEACTIVATED')),
    CONSTRAINT ck_app_user_role CHECK (global_role IN ('USER', 'SITE_ADMIN'))
);

CREATE INDEX idx_app_user_org ON app_user (organization_id);

CREATE TABLE refresh_token
(
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL,
    family_id  uuid        NOT NULL,
    token_hash text        NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);