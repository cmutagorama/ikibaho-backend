-- ---------------------------------------------------------------------------
-- ShedLock's table. One row per named job; the row IS the lock.
CREATE TABLE shedlock
(
    name       text        NOT NULL PRIMARY KEY,
    lock_until timestamptz NOT NULL,
    locked_at  timestamptz NOT NULL,
    locked_by  text        NOT NULL
);

-- ---------------------------------------------------------------------------
-- Attachments.
--
-- The row is metadata only. Bytes live in object storage and never pass through
-- this application: clients PUT straight to a presigned URL and GET from another
-- one. Proxying them would put file transfer on the same threads serving the API,
-- and make every upload's size a memory question.
CREATE TABLE attachment
(
    id           uuid PRIMARY KEY,
    issue_id     uuid        NOT NULL,
    project_id   uuid        NOT NULL,
    filename     text        NOT NULL,
    content_type text        NOT NULL,
    -- What the client claimed at request time. Replaced with the true size from
    -- object storage once the upload completes, so the two can be compared.
    size_bytes   bigint      NOT NULL,
    object_key   text        NOT NULL,
    uploaded_by  uuid        NOT NULL,
    status       text        NOT NULL,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT fk_attachment_issue FOREIGN KEY (issue_id) REFERENCES issue (id) ON DELETE CASCADE,
    CONSTRAINT fk_attachment_uploader FOREIGN KEY (uploaded_by) REFERENCES app_user (id),
    CONSTRAINT ck_attachment_status CHECK (status IN ('PENDING', 'AVAILABLE')),
    CONSTRAINT ck_attachment_size CHECK (size_bytes >= 0),
    -- Object keys are generated per attachment; a collision would mean one
    -- attachment silently serving another's bytes.
    CONSTRAINT uq_attachment_object_key UNIQUE (object_key)
);

CREATE INDEX idx_attachment_issue ON attachment (issue_id, created_at);
-- Feeds the sweeper that deletes uploads nobody ever completed.
CREATE INDEX idx_attachment_pending ON attachment (created_at) WHERE status = 'PENDING';

-- ---------------------------------------------------------------------------
-- Webhook subscriptions.
CREATE TABLE webhook
(
    id              uuid PRIMARY KEY,
    organization_id uuid        NOT NULL,
    url             text        NOT NULL,
    -- The shared secret for HMAC signing. Stored in the clear because signing
    -- requires the original -- unlike a password, it cannot be hashed. Whoever
    -- can read this table can forge deliveries, which is the reason the API
    -- never returns it after creation.
    secret          text        NOT NULL,
    description     text,
    -- Subscribed event names. A text[] rather than a join table: it is only ever
    -- read whole, and never joined against.
    event_types     text[]      NOT NULL,
    active          boolean     NOT NULL DEFAULT true,
    created_by      uuid        NOT NULL,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_webhook_org FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_webhook_creator FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT ck_webhook_events CHECK (cardinality(event_types) > 0),
    CONSTRAINT ck_webhook_url CHECK (url LIKE 'https://%' OR url LIKE 'http://%')
);

CREATE INDEX idx_webhook_org_active ON webhook (organization_id) WHERE active;

-- ---------------------------------------------------------------------------
-- Delivery attempts.
--
-- Rows are written by the event listener, inside its transaction, and sent later
-- by a scheduled job. That split is the point: a slow or hostile endpoint cannot
-- hold an event-listener thread, and a retry queue that lives in the database
-- survives a restart, which one living in a thread pool does not.
CREATE TABLE webhook_delivery
(
    id              uuid PRIMARY KEY,
    webhook_id      uuid        NOT NULL,
    organization_id uuid        NOT NULL,
    event_type      text        NOT NULL,
    event_id        uuid        NOT NULL,
    payload         text        NOT NULL,
    state           text        NOT NULL,
    attempts        int         NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    last_status     int,
    last_error      text,
    delivered_at    timestamptz,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_delivery_webhook FOREIGN KEY (webhook_id) REFERENCES webhook (id) ON DELETE CASCADE,
    CONSTRAINT ck_delivery_state CHECK (state IN ('PENDING', 'DELIVERED', 'FAILED')),
    CONSTRAINT ck_delivery_attempts CHECK (attempts >= 0)
);

-- The job's only query: what is due now. Partial, because delivered and
-- abandoned rows are the vast majority and are never polled again.
CREATE INDEX idx_delivery_due ON webhook_delivery (next_attempt_at)
    WHERE state = 'PENDING';
CREATE INDEX idx_delivery_webhook ON webhook_delivery (webhook_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Digest bookkeeping.
--
-- One row per person, recording where their last email got to. Without it a
-- digest either re-sends what it already sent or guesses from a fixed window and
-- drops anything that arrived late.
CREATE TABLE digest_state
(
    user_id      uuid PRIMARY KEY,
    last_sent_at timestamptz NOT NULL,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT fk_digest_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);