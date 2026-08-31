-- Phase 11: Google sign-in.
--
-- V7 made password_hash nullable for exactly this and left a note to extend
-- ck_app_user_authenticatable "when user_identity is introduced". This is that.

CREATE TABLE user_identity
(
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL,
    provider   text        NOT NULL,
    -- The provider's stable identifier -- Google's `sub`. Never the email:
    -- addresses get reassigned between people, `sub` does not, and keying on
    -- email would hand a recycled address the previous owner's account.
    subject    text        NOT NULL,
    -- The address at link time. Audit only; never used for lookup.
    email      text        NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_identity_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    -- One provider account maps to one Ikibaho account, globally.
    CONSTRAINT uq_identity_provider_subject UNIQUE (provider, subject),
    -- And one Ikibaho account holds at most one identity per provider, so
    -- "sign in with Google" is never ambiguous.
    CONSTRAINT uq_identity_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_identity_provider CHECK (provider IN ('GOOGLE'))
);

CREATE INDEX idx_identity_user ON user_identity (user_id);

-- ---------------------------------------------------------------------------
-- Replace the V7 invariant.
--
-- It said an ACTIVE account must have a password. That was right while a password
-- was the only credential, and it now rejects exactly the accounts this phase
-- creates. The invariant it was protecting still matters: an ACTIVE account must
-- be able to authenticate somehow.
--
-- Postgres cannot express that across two tables, so the fact is denormalized
-- onto app_user and maintained by FederatedIdentityService on link and unlink.
-- A denormalized flag can drift; FederatedIdentityTest asserts it against
-- user_identity so drift fails the build rather than locking somebody out.
ALTER TABLE app_user
    ADD COLUMN has_federated_identity boolean NOT NULL DEFAULT false;

ALTER TABLE app_user DROP CONSTRAINT ck_app_user_authenticatable;
ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_authenticatable CHECK (
        password_hash IS NOT NULL OR has_federated_identity OR status <> 'ACTIVE'
        );