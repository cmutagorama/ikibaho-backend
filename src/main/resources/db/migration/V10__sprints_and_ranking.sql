-- Phase 7: sprints, and backfilling the rank column V4 reserved for this.

-- ---------------------------------------------------------------------------
-- Sprints.
CREATE TABLE sprint
(
    id            uuid PRIMARY KEY,
    project_id    uuid        NOT NULL,
    name          text        NOT NULL,
    goal          text,
    state         text        NOT NULL,
    planned_start timestamptz,
    planned_end   timestamptz,
    started_at    timestamptz,
    completed_at  timestamptz,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    CONSTRAINT fk_sprint_project FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT ck_sprint_state CHECK (state IN ('FUTURE', 'ACTIVE', 'COMPLETED')),
    -- The state machine only moves forward, so the timestamps must agree with it.
    -- Without this a row could claim to be ACTIVE having never started.
    CONSTRAINT ck_sprint_started CHECK (state = 'FUTURE' OR started_at IS NOT NULL),
    CONSTRAINT ck_sprint_completed CHECK ((state = 'COMPLETED') = (completed_at IS NOT NULL)),
    CONSTRAINT ck_sprint_dates CHECK (planned_end IS NULL OR planned_start IS NULL
        OR planned_end > planned_start)
);

-- One active sprint per project. The service checks this first for a good error
-- message; this index is what makes the rule true under concurrency, where two
-- simultaneous starts would both pass a check-then-act.
CREATE UNIQUE INDEX uq_sprint_one_active ON sprint (project_id) WHERE state = 'ACTIVE';
CREATE INDEX idx_sprint_project ON sprint (project_id, state);

-- ---------------------------------------------------------------------------
-- Sprint scope.
--
-- A join table rather than a sprint_id on issue: sprints belong to the board
-- module, and the issue table must not grow a column only board understands.
CREATE TABLE sprint_issue
(
    sprint_id uuid NOT NULL,
    issue_id  uuid NOT NULL,
    PRIMARY KEY (sprint_id, issue_id),
    CONSTRAINT fk_sprint_issue_sprint FOREIGN KEY (sprint_id) REFERENCES sprint (id) ON DELETE CASCADE,
    -- Deleting an issue drops it from the sprint. Membership is a plan, not a
    -- record of history -- the sprint report reads issue_history for that.
    CONSTRAINT fk_sprint_issue_issue FOREIGN KEY (issue_id) REFERENCES issue (id) ON DELETE CASCADE
);

CREATE INDEX idx_sprint_issue_issue ON sprint_issue (issue_id);

-- ---------------------------------------------------------------------------
-- Backfill the LexoRank column.
--
-- Existing issues have rank NULL, which sorts last and cannot be dragged against
-- (there is no neighbour rank to bisect). Seeding them in their current order --
-- oldest first, matching how the backlog reads today -- makes every existing
-- issue immediately draggable.
--
-- Spread evenly across the space rather than numbered 1..n, so there is room
-- between any two neighbours without a rebalance. This mirrors
-- LexoRank.evenlySpaced in Java; the two must agree on the alphabet and width.
CREATE OR REPLACE FUNCTION ikibaho_base36(value bigint, width int) RETURNS text AS
$$
DECLARE
    alphabet constant text := '0123456789abcdefghijklmnopqrstuvwxyz';
    result            text := '';
    remaining         bigint := value;
BEGIN
    FOR i IN 1..width
        LOOP
            result := substr(alphabet, (remaining % 36)::int + 1, 1) || result;
            remaining := remaining / 36;
        END LOOP;
    RETURN result;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

WITH ordered AS (SELECT id,
                        row_number() OVER (PARTITION BY project_id ORDER BY created_at, id) AS position,
                        count(*) OVER (PARTITION BY project_id)                             AS total
                 FROM issue)
UPDATE issue i
-- 36^4 = 1,679,616 slots per project. GREATEST keeps the step at 1 rather than 0
-- for a project somehow larger than that, which would collapse every rank to the
-- same string and make the ordering arbitrary.
SET rank = ikibaho_base36(o.position * GREATEST(1679616 / (o.total + 1), 1), 4)
FROM ordered o
WHERE i.id = o.id;

-- Migration-only helper; the application generates ranks in Java.
DROP FUNCTION ikibaho_base36(bigint, int);

-- Every row now has one, and IssueService assigns one at creation.
ALTER TABLE issue
    ALTER COLUMN rank SET NOT NULL;

-- The board and backlog read the whole project in rank order; this index serves
-- that scan and the "lowest rank" lookup that places each new issue.
CREATE INDEX idx_issue_project_rank ON issue (project_id, rank);
