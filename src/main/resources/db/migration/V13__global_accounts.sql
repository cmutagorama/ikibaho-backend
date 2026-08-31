-- Phase 10: one account per person, membership per organization.
--
-- Before this, app_user carried organization_id and email was unique per org --
-- so charlie@gmail.com in two workspaces meant two user rows with two separate
-- passwords, and no way to know which to check at login. The account is now
-- global and organization_member carries the relationship.
--
-- This is the Jira Cloud model: an Atlassian account is global, site access is
-- granted per site. The alternative -- fully isolated tenants, each controlling
-- its own users' credentials -- is Jira Data Center's model, and the wrong shape
-- for a product where one person legitimately spans workspaces.

CREATE TABLE organization_member
(
    id              uuid PRIMARY KEY,
    organization_id uuid        NOT NULL,
    user_id         uuid        NOT NULL,
    role            text        NOT NULL,
    status          text        NOT NULL,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_member_org FOREIGN KEY (organization_id) REFERENCES organization (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    -- One membership per person per workspace. Two would make "what is my role
    -- here" ambiguous, and that question is answered on every request.
    CONSTRAINT uq_member UNIQUE (organization_id, user_id),
    CONSTRAINT ck_member_role CHECK (role IN ('MEMBER', 'ADMIN')),
    CONSTRAINT ck_member_status CHECK (status IN ('INVITED', 'ACTIVE', 'DEACTIVATED'))
);

-- Login asks "which workspaces may this person enter"; partial, because the
-- inactive ones are never the answer.
CREATE INDEX idx_member_user_active ON organization_member (user_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_member_org ON organization_member (organization_id);

-- ---------------------------------------------------------------------------
-- Backfill BEFORE the column it reads is dropped.
--
-- gen_random_uuid() is v4 where BaseEntity generates v7. Accepted here: these
-- rows are written once, and the index locality v7 buys is irrelevant at
-- backfill volume.
INSERT INTO organization_member (id, organization_id, user_id, role, status, created_at, updated_at)
SELECT gen_random_uuid(),
       u.organization_id,
       u.id,
       CASE u.global_role WHEN 'SITE_ADMIN' THEN 'ADMIN' ELSE 'MEMBER' END,
       u.status,
       u.created_at,
       u.updated_at
FROM app_user u;

-- ---------------------------------------------------------------------------
-- Refresh tokens become per-workspace.
--
-- Without this a refresh would silently drop the user into whichever workspace
-- came back first, because the token would no longer know which one it was
-- issued for.
ALTER TABLE refresh_token ADD COLUMN organization_id uuid;

UPDATE refresh_token r
SET organization_id = (SELECT m.organization_id
                       FROM organization_member m
                       WHERE m.user_id = r.user_id
                       LIMIT 1);

-- Tokens whose user has no membership at all cannot be refreshed into anything.
DELETE FROM refresh_token WHERE organization_id IS NULL;

ALTER TABLE refresh_token ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE refresh_token ADD CONSTRAINT fk_refresh_token_org
    FOREIGN KEY (organization_id) REFERENCES organization (id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- Collapse the user row to the account it always should have been.
--
-- Adding the global unique constraint FAILS if the same address exists in two
-- organizations. That is the correct outcome: it cannot currently happen, because
-- the application enforced global uniqueness even though the schema did not --
-- but a loud failure beats silently keeping one row and orphaning another.
ALTER TABLE app_user DROP CONSTRAINT uq_app_user_org_email;
ALTER TABLE app_user ADD CONSTRAINT uq_app_user_email UNIQUE (email);

DROP INDEX idx_app_user_org;
ALTER TABLE app_user DROP CONSTRAINT fk_app_user_org;
ALTER TABLE app_user DROP COLUMN organization_id;

-- global_role moves to the membership; ck_app_user_role is dropped with the column.
ALTER TABLE app_user DROP COLUMN global_role;

-- ck_app_user_authenticatable (V7) still applies unchanged: app_user.status now
-- means "can this account authenticate at all", which is exactly what it guards.
-- Per-workspace access is organization_member.status.
