-- Phase 6: events, activity and in-app notifications.
--
-- Three tables, one theme: everything here is written by a listener reacting to
-- something that already committed elsewhere, never by the request that caused it.

-- ---------------------------------------------------------------------------
-- Spring Modulith's event publication registry -- the transactional outbox.
--
-- A publication row is INSERTed in the SAME transaction as the change that
-- triggered it, and UPDATEd with a completion_date once its listener returns.
-- That is what makes the pair atomic: there is no window where the issue moved
-- but the event was lost, and none where the event fires for a rolled-back edit.
--
-- Hibernate owns this mapping (spring-modulith-events-jpa), and ddl-auto is
-- 'validate', so the columns below must match its entity exactly. They were
-- taken from Hibernate's own schema export rather than written by hand.
--
-- Deviation from that export, deliberate: the text columns are `text` rather
-- than varchar(255). Hibernate defaults an unannotated String to 255, but
-- serialized_event holds the event's entire JSON body -- IssueCreated already
-- exceeds 255 characters. Postgres reports both as VARCHAR to JDBC, so validate
-- accepts `text`; a varchar(255) would instead throw on insert at runtime.
CREATE TABLE event_publication
(
    id                     uuid PRIMARY KEY,
    listener_id            text        NOT NULL,
    event_type             text        NOT NULL,
    serialized_event       text        NOT NULL,
    publication_date       timestamptz NOT NULL,
    completion_date        timestamptz,
    status                 text,
    last_resubmission_date timestamptz,
    completion_attempts    int         NOT NULL DEFAULT 0,
    CONSTRAINT ck_event_publication_status
        CHECK (status IN ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED'))
);

-- The registry's hot query on startup and on retry: "what has not completed?".
-- Partial, because completed rows are the overwhelming majority and are of no
-- interest to it.
CREATE INDEX idx_event_publication_incomplete
    ON event_publication (publication_date) WHERE completion_date IS NULL;

-- Completion looks a publication up by listener + payload, so this pair carries
-- the write path. Hashing the payload keeps the index small; the column itself
-- is unbounded.
CREATE INDEX idx_event_publication_completion
    ON event_publication (listener_id, md5(serialized_event));

-- ---------------------------------------------------------------------------
-- Issue history: the audit trail, written only by activity's listener.
CREATE TABLE issue_history
(
    id           uuid PRIMARY KEY,
    issue_id     uuid        NOT NULL,
    project_id   uuid        NOT NULL,
    issue_key    text        NOT NULL,
    actor_id     uuid,
    kind         text        NOT NULL,
    field        text,
    old_value    text,
    new_value    text,
    reference_id uuid,
    -- When it happened in the domain, which is NOT created_at (when the row was
    -- written). A retried listener writes late; history must still read in order.
    occurred_at  timestamptz NOT NULL,
    dedupe_key   text        NOT NULL,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT ck_issue_history_kind
        CHECK (kind IN ('CREATED', 'TRANSITIONED', 'ASSIGNED', 'COMMENTED')),
    -- The registry is at-least-once: a listener that fails, or an app killed
    -- mid-handler, sees the same event again. For an audit log a duplicate line
    -- is a defect, so the database refuses it rather than the recorder guessing.
    CONSTRAINT uq_issue_history_dedupe UNIQUE (dedupe_key)
);

-- No FK to issue: history outlives the issue it describes, and a hard delete
-- must not take the record of what happened with it.
CREATE INDEX idx_issue_history_issue ON issue_history (issue_id, occurred_at DESC);
CREATE INDEX idx_issue_history_project ON issue_history (project_id, occurred_at DESC);

-- ---------------------------------------------------------------------------
-- In-app notifications.
CREATE TABLE notification
(
    id           uuid        NOT NULL PRIMARY KEY,
    recipient_id uuid        NOT NULL,
    kind         text        NOT NULL,
    title        text        NOT NULL,
    body         text,
    issue_id     uuid,
    issue_key    text,
    project_id   uuid,
    read_at      timestamptz,
    occurred_at  timestamptz NOT NULL,
    dedupe_key   text        NOT NULL,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_notification_kind
        CHECK (kind IN ('ISSUE_ASSIGNED', 'ISSUE_UNASSIGNED', 'ISSUE_COMMENTED')),
    CONSTRAINT uq_notification_dedupe UNIQUE (dedupe_key)
);

-- The inbox query, and the unread badge that every page load asks for. Partial
-- on unread: the badge only ever counts those, and they are the small minority.
CREATE INDEX idx_notification_inbox ON notification (recipient_id, occurred_at DESC);
CREATE INDEX idx_notification_unread ON notification (recipient_id) WHERE read_at IS NULL;
