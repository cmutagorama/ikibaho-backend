-- Invitations let a second user join an existing organization. Until now the only
-- way in was self-serve registration, which always creates a NEW org -- so two users
-- could never share a tenant without direct SQL.
CREATE TABLE invitation
(
    id              uuid PRIMARY KEY,
    organization_id uuid        NOT NULL,
    user_id         uuid        NOT NULL,
    invited_by      uuid        NOT NULL,
    token_hash      text        NOT NULL,
    expires_at      timestamptz NOT NULL,
    accepted_at     timestamptz,
    revoked_at      timestamptz,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT fk_invitation_org FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_invitation_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_invitation_invited_by FOREIGN KEY (invited_by) REFERENCES app_user (id),
    -- Same discipline as refresh_token: only the hash is stored, so a database
    -- leak does not hand out working invitation links.
    CONSTRAINT uq_invitation_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_invitation_user ON invitation (user_id);

-- At most one live invitation per user; accepted or revoked ones may pile up.
CREATE UNIQUE INDEX uq_invitation_pending ON invitation (user_id) WHERE accepted_at IS NULL AND revoked_at IS NULL;
