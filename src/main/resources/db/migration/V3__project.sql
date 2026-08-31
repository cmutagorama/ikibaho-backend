CREATE TABLE user_group
(
    id              uuid PRIMARY KEY,
    organization_id uuid        NOT NULL,
    name            text        NOT NULL,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_user_group_org FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT uq_user_group_name UNIQUE (organization_id, name)
);

CREATE TABLE group_member
(
    group_id uuid NOT NULL,
    user_id  uuid NOT NULL,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id) REFERENCES user_group (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_member_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX idx_group_member_user ON group_member (user_id);

-- ---------- permission schemes ----------
CREATE TABLE permission_scheme
(
    id              uuid PRIMARY KEY,
    organization_id uuid        NOT NULL,
    name            text        NOT NULL,
    is_default      boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_permission_scheme_org FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT uq_permission_scheme_name UNIQUE (organization_id, name)
);

-- ---------- project roles: ORG-LEVEL DEFINITIONS ----------
CREATE TABLE project_role
(
    id              uuid PRIMARY KEY,
    organization_id uuid        NOT NULL,
    name            text        NOT NULL,
    description     text,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_project_role_org FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT uq_project_role_name UNIQUE (organization_id, name)
);

-- ---------- projects ----------
CREATE TABLE project
(
    id                   uuid PRIMARY KEY,
    organization_id      uuid        NOT NULL,
    key                  text        NOT NULL,
    name                 text        NOT NULL,
    description          text,
    lead_id              uuid        NOT NULL,
    permission_scheme_id uuid        NOT NULL,
    issue_counter        bigint      NOT NULL DEFAULT 0,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    deleted              boolean     NOT NULL DEFAULT false, -- Hibernate keys off this
    deleted_at           timestamptz,                        -- audit only, never mapped
    CONSTRAINT fk_project_org FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_project_lead FOREIGN KEY (lead_id) REFERENCES app_user (id),
    CONSTRAINT fk_project_scheme FOREIGN KEY (permission_scheme_id) REFERENCES permission_scheme (id),
    CONSTRAINT ck_project_key CHECK (key ~ '^[A-Z][A-Z0-9]{1,9}$'
) ,
    -- the two columns must never disagree
    CONSTRAINT ck_project_deleted_consistent CHECK (
        (deleted = false AND deleted_at IS NULL) OR
        (deleted = true  AND deleted_at IS NOT NULL)
    )
);

-- Partial unique index: a soft-deleted project frees its key for reuse.
CREATE UNIQUE INDEX uq_project_key ON project (organization_id, key) WHERE deleted = false;

-- ---------- role actors: PER-PROJECT ASSIGNMENTS ----------
CREATE TABLE project_role_actor
(
    id         uuid PRIMARY KEY,
    project_id uuid        NOT NULL,
    role_id    uuid        NOT NULL,
    user_id    uuid,
    group_id   uuid,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_role_actor_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_actor_role FOREIGN KEY (role_id) REFERENCES project_role (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_actor_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_actor_group FOREIGN KEY (group_id) REFERENCES user_group (id) ON DELETE CASCADE,
    CONSTRAINT ck_role_actor_one_holder CHECK (
        (user_id IS NOT NULL AND group_id IS NULL) OR
        (user_id IS NULL AND group_id IS NOT NULL)
        ),
    CONSTRAINT uq_role_actor UNIQUE NULLS NOT DISTINCT (project_id, role_id, user_id, group_id)
);
CREATE INDEX idx_role_actor_project_user ON project_role_actor (project_id, user_id);
CREATE INDEX idx_role_actor_group ON project_role_actor (group_id);

-- ---------- grants ----------
CREATE TABLE permission_grant
(
    id          uuid PRIMARY KEY,
    scheme_id   uuid        NOT NULL,
    permission  text        NOT NULL,
    holder_type text        NOT NULL,
    holder_ref  uuid,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    CONSTRAINT fk_permission_grant_scheme FOREIGN KEY (scheme_id) REFERENCES permission_scheme (id) ON DELETE CASCADE,
    CONSTRAINT ck_permission_grant_holder CHECK (
        (holder_type IN ('PROJECT_ROLE', 'GROUP', 'USER') AND holder_ref IS NOT NULL) OR
        (holder_type IN ('REPORTER', 'ASSIGNEE', 'PROJECT_LEAD', 'ANY_LOGGED_IN') AND holder_ref IS NULL)
        ),
    CONSTRAINT uq_permission_grant UNIQUE NULLS NOT DISTINCT (scheme_id, permission, holder_type, holder_ref)
);
CREATE INDEX idx_permission_grant_scheme ON permission_grant (scheme_id, permission);