# Mandatory Feature Branch Roadmap

## Objective

Move Smart Task Manager from a local demonstration into a credible AI task-manager MVP. Each mandatory capability is delivered on a dedicated branch. A branch is merged into `main` only after its focused tests, relevant full suites, production build, code review, and clean-worktree checks pass.

## Delivery sequence

1. `feature/ownership-authorization` — derive identity from the authenticated principal and scope project, task, and user operations.
2. `feature/manual-project-task-lifecycle` — create and manage projects and tickets without requiring AI.
3. `feature/personal-work-assignments` — introduce explicit project participation and make My Work assignee-aware.
4. `feature/existing-project-ai-planning` — add project-scoped planning modes with an editable confirm boundary.
5. `feature/planning-run-recovery` — list, resume, retry, and discard durable AI planning runs.
6. `feature/onboarding-session-hardening` — expose registration/account flows and require production-safe session configuration.

Every branch starts from the newly verified `main`, so later work can rely on the invariants established by earlier branches. Feature branches remain available after merging; they are not deleted automatically.

## Product boundaries

- Spring Boot owns authentication, authorization, validation, transactions, and PostgreSQL writes.
- FastAPI/LangGraph returns structured planning proposals and never writes business data directly.
- AI remains optional for ordinary task management.
- Existing-project AI changes use the same editable draft and explicit confirmation boundary as initial generation.
- Until project membership ships, only the project owner may read or mutate a project and its tickets.

## Merge gate

A feature is mergeable only when:

- new behavior was driven by a witnessed red-to-green test cycle;
- negative authorization and validation paths are covered;
- all affected stack tests pass;
- frontend changes build and pass a real-browser smoke test when applicable;
- backend changes compile with Java 21;
- review finds no unresolved critical or important issue;
- `git diff --check` passes and the feature worktree is clean after commit.
