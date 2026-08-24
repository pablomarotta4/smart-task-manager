ALTER TABLE projects
    ADD COLUMN objective TEXT;

CREATE TABLE project_generation_runs (
    id UUID PRIMARY KEY,
    requested_by BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    prompt TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    draft_json TEXT,
    quality_json TEXT,
    revision_count INT,
    model_name VARCHAR(100),
    error_code VARCHAR(64),
    project_id BIGINT REFERENCES projects(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_project_generation_runs_requester
    ON project_generation_runs(requested_by);
CREATE INDEX idx_project_generation_runs_status
    ON project_generation_runs(status);

ALTER TABLE tasks
    ADD COLUMN planning_client_id VARCHAR(50),
    ADD COLUMN estimated_hours NUMERIC(6, 2),
    ADD COLUMN generation_run_id UUID REFERENCES project_generation_runs(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uq_tasks_generation_client_id
    ON tasks(generation_run_id, planning_client_id)
    WHERE generation_run_id IS NOT NULL AND planning_client_id IS NOT NULL;
CREATE INDEX idx_tasks_generation_run
    ON tasks(generation_run_id);

CREATE TABLE task_acceptance_criteria (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    criterion TEXT NOT NULL,
    position INT NOT NULL,
    CONSTRAINT uq_task_acceptance_criterion_position UNIQUE (task_id, position)
);

CREATE INDEX idx_task_acceptance_criteria_task
    ON task_acceptance_criteria(task_id);

CREATE TABLE task_dependencies (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    depends_on_task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT ck_task_dependency_not_self CHECK (task_id <> depends_on_task_id),
    CONSTRAINT uq_task_dependency UNIQUE (task_id, depends_on_task_id)
);

CREATE INDEX idx_task_dependencies_task
    ON task_dependencies(task_id);
CREATE INDEX idx_task_dependencies_depends_on
    ON task_dependencies(depends_on_task_id);
