CREATE TABLE status
(
    id              uuid PRIMARY KEY,
    organization_id uuid        NOT NULL,
    name            text        NOT NULL,
    category        text        NOT NULL,
    position        int         NOT NULL,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_status_org FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT uq_status_name UNIQUE (organization_id, name),
    CONSTRAINT ck_status_category CHECK (category IN ('TODO', 'IN_PROGRESS', 'DONE'))
);

CREATE TABLE issue_type
(
    id              uuid PRIMARY KEY,
    organization_id uuid        NOT NULL,
    name            text        NOT NULL,
    icon            text,
    hierarchy_level int         NOT NULL, -- 1 = epic, 0 = standard, -1 = subtask
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_issue_type_org FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT uq_issue_type_name UNIQUE (organization_id, name),
    CONSTRAINT ck_issue_type_level CHECK (hierarchy_level BETWEEN -1 AND 1)
);

CREATE TABLE custom_field
(
    id              uuid PRIMARY KEY,
    organization_id uuid        NOT NULL,
    project_id      uuid, -- NULL = applies org-wide
    field_key       text        NOT NULL,
    name            text        NOT NULL,
    field_type      text        NOT NULL,
    config          jsonb       NOT NULL DEFAULT '{}',
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_custom_field_org FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_custom_field_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT ck_custom_field_type CHECK (field_type IN
                                           ('TEXT', 'NUMBER', 'DATE', 'SELECT', 'MULTI_SELECT', 'USER', 'CHECKBOX')),
    CONSTRAINT uq_custom_field_key UNIQUE NULLS NOT DISTINCT (organization_id, project_id, field_key)
);

CREATE TABLE issue
(
    id              uuid PRIMARY KEY,
    organization_id uuid        NOT NULL,
    project_id      uuid        NOT NULL,
    issue_key       text        NOT NULL,
    issue_number    bigint      NOT NULL,
    type_id         uuid        NOT NULL,
    status_id       uuid        NOT NULL,
    priority        text        NOT NULL DEFAULT 'MEDIUM',
    summary         text        NOT NULL,
    description     text,
    reporter_id     uuid        NOT NULL,
    assignee_id     uuid,
    parent_id       uuid,
    rank            text, -- LexoRank, populated in Phase 7
    story_points    numeric(6, 2),
    due_date        date,
    custom_fields   jsonb       NOT NULL DEFAULT '{}',
    version         bigint      NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    deleted         boolean     NOT NULL DEFAULT false,
    deleted_at      timestamptz,
    CONSTRAINT fk_issue_org FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_issue_project FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_issue_type FOREIGN KEY (type_id) REFERENCES issue_type (id),
    CONSTRAINT fk_issue_status FOREIGN KEY (status_id) REFERENCES status (id),
    CONSTRAINT fk_issue_reporter FOREIGN KEY (reporter_id) REFERENCES app_user (id),
    CONSTRAINT fk_issue_assignee FOREIGN KEY (assignee_id) REFERENCES app_user (id),
    CONSTRAINT fk_issue_parent FOREIGN KEY (parent_id) REFERENCES issue (id),
    CONSTRAINT ck_issue_priority CHECK (priority IN ('LOWEST', 'LOW', 'MEDIUM', 'HIGH', 'HIGHEST')),
    CONSTRAINT ck_issue_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_issue_deleted_consistent CHECK (
        (deleted = false AND deleted_at IS NULL) OR
        (deleted = true AND deleted_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_issue_key ON issue (issue_key) WHERE deleted = false;
CREATE INDEX idx_issue_project_status ON issue (project_id, status_id) WHERE deleted = false;
CREATE INDEX idx_issue_assignee ON issue (assignee_id) WHERE deleted = false;
CREATE INDEX idx_issue_parent ON issue (parent_id);
CREATE INDEX idx_issue_custom_fields ON issue USING gin (custom_fields jsonb_path_ops);
-- supports keyset pagination: ORDER BY created_at DESC, id DESC
CREATE INDEX idx_issue_project_created ON issue (project_id, created_at DESC, id DESC) WHERE deleted = false;

CREATE TABLE comment
(
    id         uuid PRIMARY KEY,
    issue_id   uuid        NOT NULL,
    author_id  uuid        NOT NULL,
    body       text        NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted    boolean     NOT NULL DEFAULT false,
    deleted_at timestamptz,
    CONSTRAINT fk_comment_issue FOREIGN KEY (issue_id) REFERENCES issue (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES app_user (id),
    CONSTRAINT ck_comment_deleted_consistent CHECK (
        (deleted = false AND deleted_at IS NULL) OR
        (deleted = true AND deleted_at IS NOT NULL)
        )
);
CREATE INDEX idx_comment_issue ON comment (issue_id, created_at) WHERE deleted = false;

CREATE TABLE issue_link
(
    id         uuid PRIMARY KEY,
    source_id  uuid        NOT NULL,
    target_id  uuid        NOT NULL,
    link_type  text        NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_issue_link_source FOREIGN KEY (source_id) REFERENCES issue (id) ON DELETE CASCADE,
    CONSTRAINT fk_issue_link_target FOREIGN KEY (target_id) REFERENCES issue (id) ON DELETE CASCADE,
    CONSTRAINT ck_issue_link_type CHECK (link_type IN ('BLOCKS', 'RELATES', 'DUPLICATES', 'CLONES')),
    CONSTRAINT ck_issue_link_self CHECK (source_id <> target_id),
    CONSTRAINT uq_issue_link UNIQUE (source_id, target_id, link_type)
);