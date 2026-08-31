-- A federated user (SSO / LDAP) has no local password. Making the column nullable
-- now is a one-line change; doing it after real data exists is a data migration.
ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL;

-- Guard the invariant the NOT NULL used to carry: an ACTIVE account must be able to
-- authenticate somehow. Until federated identities exist, that means a password.
-- Extend this constraint when user_identity is introduced.
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_authenticatable CHECK (
    password_hash IS NOT NULL OR status <> 'ACTIVE'
);
