-- Phase 8: full-text search, JQL, and saved filters.
--
-- The search module owns this table outright. It is a denormalized copy of what
-- a search needs, kept current by listeners on issue events -- not a view over
-- the issue tables.
--
-- Two reasons. Boundaries: search must not read tables the issue module owns.
-- And speed: a JQL query mixes free text with status, assignee and type filters,
-- so against the live schema every search would join issue, status, issue_type,
-- project and comment. Here it is one indexed scan of one table.
CREATE TABLE issue_search_index
(
    issue_id        uuid PRIMARY KEY,
    organization_id uuid        NOT NULL,
    project_id      uuid        NOT NULL,
    project_key     text        NOT NULL,
    issue_key       text        NOT NULL,
    issue_number    bigint      NOT NULL,
    summary         text        NOT NULL,
    description     text,
    type_id         uuid        NOT NULL,
    type_name       text        NOT NULL,
    status_id       uuid        NOT NULL,
    status_name     text        NOT NULL,
    status_category text        NOT NULL,
    priority        text        NOT NULL,
    assignee_id     uuid,
    reporter_id     uuid        NOT NULL,
    parent_id       uuid,
    story_points    numeric(6, 2),
    due_date        date,
    rank            text,
    -- Every comment concatenated. Searchable, never rendered from here.
    comment_text    text,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,

    -- A generated column, not a trigger: Postgres recomputes it on every write
    -- and it can never drift from the columns it summarises. Triggers can be
    -- disabled, forgotten in a bulk load, or skipped by COPY.
    --
    -- Weighted so relevance ranking is meaningful. A hit in the key or summary
    -- outranks one buried in a comment thread, which is what someone typing a
    -- couple of words actually means.
    search_vector   tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(issue_key, '') || ' ' || coalesce(summary, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(comment_text, '')), 'C')
        ) STORED,

    CONSTRAINT fk_search_issue FOREIGN KEY (issue_id) REFERENCES issue (id) ON DELETE CASCADE
);

-- GIN, not GiST: this index is read far more than it is written, and GIN answers
-- @@ queries substantially faster at the cost of slower updates.
CREATE INDEX idx_search_vector ON issue_search_index USING GIN (search_vector);

-- Every search is scoped to projects the user may browse, so project_id leads
-- each of these. Without it, a query for "assignee = me" scans every tenant's
-- issues and then discards most of them.
CREATE INDEX idx_search_project ON issue_search_index (project_id, updated_at DESC);
CREATE INDEX idx_search_assignee ON issue_search_index (assignee_id, project_id);
CREATE INDEX idx_search_status ON issue_search_index (project_id, status_id);
CREATE INDEX idx_search_org_key ON issue_search_index (organization_id, issue_key);

-- ---------------------------------------------------------------------------
-- Backfill everything that already exists.
--
-- The listener only sees issues that change from now on, so without this the
-- index would start empty and fill in slowly as people happened to edit things.
INSERT INTO issue_search_index (issue_id, organization_id, project_id, project_key, issue_key,
                                issue_number, summary, description, type_id, type_name,
                                status_id, status_name, status_category, priority,
                                assignee_id, reporter_id, parent_id, story_points, due_date,
                                rank, comment_text, created_at, updated_at)
SELECT i.id,
       i.organization_id,
       i.project_id,
       p.key,
       i.issue_key,
       i.issue_number,
       i.summary,
       i.description,
       i.type_id,
       t.name,
       i.status_id,
       s.name,
       s.category,
       i.priority,
       i.assignee_id,
       i.reporter_id,
       i.parent_id,
       i.story_points,
       i.due_date,
       i.rank,
       (SELECT string_agg(c.body, ' ') FROM comment c WHERE c.issue_id = i.id AND NOT c.deleted),
       i.created_at,
       i.updated_at
FROM issue i
         JOIN project p ON p.id = i.project_id
         JOIN status s ON s.id = i.status_id
         JOIN issue_type t ON t.id = i.type_id
WHERE NOT i.deleted;

-- ---------------------------------------------------------------------------
-- Saved filters.
CREATE TABLE saved_filter
(
    id              uuid PRIMARY KEY,
    organization_id uuid        NOT NULL,
    owner_id        uuid        NOT NULL,
    name            text        NOT NULL,
    jql             text        NOT NULL,
    description     text,
    -- Shared filters are readable org-wide but editable only by their owner.
    -- Sharing a query is not the same as sharing its results: the JQL runs under
    -- the *reader's* permissions, so a shared filter never widens what they see.
    shared          boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_filter_org FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_filter_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_filter_name_per_owner UNIQUE (owner_id, name)
);

CREATE INDEX idx_filter_owner ON saved_filter (owner_id);
CREATE INDEX idx_filter_shared ON saved_filter (organization_id) WHERE shared;