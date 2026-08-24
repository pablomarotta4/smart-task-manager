ALTER TABLE project_generation_runs
    ADD COLUMN mode VARCHAR(32) NOT NULL DEFAULT 'NEW_PROJECT',
    ADD COLUMN target_task_id BIGINT REFERENCES tasks(id) ON DELETE SET NULL,
    ADD COLUMN context_hash VARCHAR(64);

CREATE INDEX idx_project_generation_runs_target_task
    ON project_generation_runs(target_task_id);

ALTER TABLE tasks
    ADD COLUMN parent_task_id BIGINT REFERENCES tasks(id) ON DELETE SET NULL;

CREATE INDEX idx_tasks_parent_task
    ON tasks(parent_task_id);
