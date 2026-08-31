-- Tenant root. Every tenant-owned table will carry organization_id.
CREATE TABLE organization
(
    id         uuid PRIMARY KEY,
    name       text        NOT NULL,
    slug       text        NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_organization_slug UNIQUE (slug)
);