ALTER TABLE project_generation_runs
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 1;

ALTER TABLE project_generation_runs
    ADD CONSTRAINT ck_project_generation_runs_attempt_count CHECK (attempt_count >= 1);
