CREATE
OR REPLACE FUNCTION set_deleted_at() RETURNS trigger AS $$
BEGIN
    IF
NEW.deleted AND NOT OLD.deleted THEN
        NEW.deleted_at := now();
    ELSIF
NOT NEW.deleted AND OLD.deleted THEN
        NEW.deleted_at := NULL;
END IF;
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

-- BEFORE UPDATE runs prior to CHECK evaluation, so the constraint sees the
-- corrected row. Hibernate sets `deleted`; the trigger keeps `deleted_at` in step.
CREATE TRIGGER trg_issue_deleted_at
    BEFORE UPDATE
    ON issue
    FOR EACH ROW EXECUTE FUNCTION set_deleted_at();

CREATE TRIGGER trg_comment_deleted_at
    BEFORE UPDATE
    ON comment
    FOR EACH ROW EXECUTE FUNCTION set_deleted_at();

CREATE TRIGGER trg_project_deleted_at
    BEFORE UPDATE
    ON project
    FOR EACH ROW EXECUTE FUNCTION set_deleted_at();