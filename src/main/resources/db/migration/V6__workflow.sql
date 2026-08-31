CREATE TABLE workflow
(
    id                uuid PRIMARY KEY,
    organization_id   uuid        NOT NULL,
    name              text        NOT NULL,
    initial_status_id uuid        NOT NULL,
    is_default        boolean     NOT NULL DEFAULT false,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    CONSTRAINT fk_workflow_org FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_workflow_initial FOREIGN KEY (initial_status_id) REFERENCES status (id),
    CONSTRAINT uq_workflow_name UNIQUE (organization_id, name)
);

CREATE TABLE transition
(
    id             uuid PRIMARY KEY,
    workflow_id    uuid        NOT NULL,
    name           text        NOT NULL,
    from_status_id uuid, -- NULL = global: reachable from any status
    to_status_id   uuid        NOT NULL,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    CONSTRAINT fk_transition_workflow FOREIGN KEY (workflow_id) REFERENCES workflow (id) ON DELETE CASCADE,
    CONSTRAINT fk_transition_from FOREIGN KEY (from_status_id) REFERENCES status (id),
    CONSTRAINT fk_transition_to FOREIGN KEY (to_status_id) REFERENCES status (id),
    CONSTRAINT ck_transition_not_self CHECK (from_status_id IS NULL OR from_status_id <> to_status_id),
    CONSTRAINT uq_transition UNIQUE NULLS NOT DISTINCT (workflow_id, from_status_id, to_status_id)
);
CREATE INDEX idx_transition_workflow_from ON transition (workflow_id, from_status_id);

CREATE TABLE transition_rule
(
    id            uuid PRIMARY KEY,
    transition_id uuid        NOT NULL,
    kind          text        NOT NULL,
    rule_type     text        NOT NULL,
    config        jsonb       NOT NULL DEFAULT '{}',
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    CONSTRAINT fk_transition_rule FOREIGN KEY (transition_id) REFERENCES transition (id) ON DELETE CASCADE,
    CONSTRAINT ck_transition_rule_kind CHECK (kind IN ('CONDITION', 'VALIDATOR', 'POST_FUNCTION'))
);
CREATE INDEX idx_transition_rule_transition ON transition_rule (transition_id, kind);

ALTER TABLE project
    ADD COLUMN workflow_id uuid;
ALTER TABLE project
    ADD CONSTRAINT fk_project_workflow
        FOREIGN KEY (workflow_id) REFERENCES workflow (id);

-- ---------- backfill: one default workflow per org that already has statuses ----------
INSERT INTO workflow (id, organization_id, name, initial_status_id, is_default, created_at, updated_at)
SELECT gen_random_uuid(),
       o.id,
       'Default Workflow',
       (SELECT s.id FROM status s WHERE s.organization_id = o.id ORDER BY s.position LIMIT 1),
       true, now(), now()
FROM organization o
WHERE EXISTS (SELECT 1 FROM status s WHERE s.organization_id = o.id);

-- Global transitions to every status: preserves today's permissive behaviour exactly,
-- so no existing issue becomes unreachable from its current state.
INSERT INTO transition (id, workflow_id, name, from_status_id, to_status_id, created_at, updated_at)
SELECT gen_random_uuid(), w.id, s.name, NULL, s.id, now(), now()
FROM workflow w
         JOIN status s ON s.organization_id = w.organization_id;

UPDATE project p
SET workflow_id = w.id FROM workflow w
WHERE w.organization_id = p.organization_id AND w.is_default;