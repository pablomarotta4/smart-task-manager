CREATE TABLE project_memberships (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_project_membership UNIQUE (project_id, user_id)
);

CREATE INDEX idx_project_memberships_user
    ON project_memberships(user_id);

INSERT INTO project_memberships (project_id, user_id)
SELECT id, owner_id
FROM projects
ON CONFLICT (project_id, user_id) DO NOTHING;
