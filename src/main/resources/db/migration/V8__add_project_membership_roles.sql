ALTER TABLE project_memberships
    ADD COLUMN role VARCHAR(16) DEFAULT 'MEMBER';

UPDATE project_memberships
SET role = 'MEMBER';

INSERT INTO project_memberships (project_id, user_id, role)
SELECT id, owner_id, 'OWNER'
FROM projects
ON CONFLICT (project_id, user_id)
DO UPDATE SET role = 'OWNER';

UPDATE project_memberships
SET role = 'MEMBER'
WHERE role IS NULL OR role NOT IN ('OWNER', 'MANAGER', 'MEMBER');

ALTER TABLE project_memberships
    ALTER COLUMN role SET NOT NULL,
    ADD CONSTRAINT chk_project_membership_role
        CHECK (role IN ('OWNER', 'MANAGER', 'MEMBER'));

UPDATE tasks task
SET assignee_id = NULL
WHERE task.assignee_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM project_memberships membership
      WHERE membership.project_id = task.project_id
        AND membership.user_id = task.assignee_id
  );

ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_project_assignee_membership
        FOREIGN KEY (project_id, assignee_id)
        REFERENCES project_memberships(project_id, user_id)
        ON DELETE SET NULL (assignee_id);

CREATE UNIQUE INDEX uq_project_membership_single_owner
    ON project_memberships(project_id)
    WHERE role = 'OWNER';

CREATE OR REPLACE FUNCTION prevent_project_membership_identity_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.project_id IS DISTINCT FROM NEW.project_id
            OR OLD.user_id IS DISTINCT FROM NEW.user_id THEN
        RAISE EXCEPTION 'Project membership identity cannot be changed';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER project_membership_identity_is_immutable
BEFORE UPDATE OF project_id, user_id ON project_memberships
FOR EACH ROW
EXECUTE FUNCTION prevent_project_membership_identity_change();

CREATE OR REPLACE FUNCTION verify_project_owner_membership()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    affected_project_id BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'projects' THEN
        affected_project_id := CASE
            WHEN TG_OP = 'DELETE' THEN OLD.id
            ELSE NEW.id
        END;
    ELSE
        affected_project_id := CASE
            WHEN TG_OP = 'DELETE' THEN OLD.project_id
            ELSE NEW.project_id
        END;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM projects WHERE id = affected_project_id) THEN
        RETURN NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM projects project
        JOIN project_memberships membership
            ON membership.project_id = project.id
           AND membership.user_id = project.owner_id
           AND membership.role = 'OWNER'
        WHERE project.id = affected_project_id
    ) THEN
        RAISE EXCEPTION 'Project % must have its owner as the OWNER membership', affected_project_id;
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER project_owner_membership_after_project_change
AFTER INSERT OR UPDATE OF owner_id OR DELETE ON projects
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION verify_project_owner_membership();

CREATE CONSTRAINT TRIGGER project_owner_membership_after_membership_change
AFTER INSERT OR UPDATE OR DELETE ON project_memberships
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION verify_project_owner_membership();
