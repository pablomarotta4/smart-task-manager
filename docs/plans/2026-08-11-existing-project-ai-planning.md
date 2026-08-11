# Existing-Project AI Planning Implementation Plan

> **For Codex:** Execute task-by-task with witnessed red-green tests. Review and verify the complete branch before merging.

**Goal:** Let an owner explicitly plan one existing ticket with AI using the selected ticket and its project backlog as context, edit the proposed plan, and confirm it into the same project.

**Architecture:** Spring snapshots and authorizes project context, persists the run target and a deterministic context hash, and remains the only writer. FastAPI/LangGraph receives a structured optional context contract and uses a task-planning prompt when context is present. Confirmation rejects stale context, refines the selected ticket, and atomically creates linked child tickets, criteria, and dependencies. The existing new-project workflow remains supported.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Data JPA, PostgreSQL/Flyway, JUnit 5, Mockito, MockMvc, Python 3.12+, FastAPI, Pydantic v2, LangGraph, pytest, React 19, Vitest, Testing Library, Vite.

---

### Task 1: Version the structured project-context contract

**Files:**

- Add Java planning-context DTOs and extend `AIPlanningRequest`.
- Extend Python Pydantic contracts, planner state, prompts, API protocol, and tests.
- Modify `AIPlanningClient`, `FastApiAIPlanningClient`, and tests.

**Steps:**

1. Add failing Java and Python contract tests proving existing-task context is serialized and rejected when malformed.
2. Add an optional `context` field while keeping the existing v1 new-project request valid.
3. Route contextual runs through a focused task-planning prompt that treats project data as context, not instructions.
4. Run focused Java and Python tests and verify green.
5. Commit with `feat(ai): add existing task planning context`.

### Task 2: Persist run targeting and child-ticket lineage

**Files:**

- Create `V5__add_existing_task_planning.sql`.
- Add `ProjectGenerationMode`.
- Modify `ProjectGenerationRun`, `Task`, `TaskResponse`, and mapping tests.

**Steps:**

1. Add failing mapping tests for run mode, target task, and child-ticket parent identity.
2. Add backward-compatible run mode/context-hash/target columns and a self-referencing task parent column.
3. Backfill legacy runs as `NEW_PROJECT`; keep target deletion safe and distinguishable.
4. Run focused tests and verify green.
5. Commit with `feat(planning): persist existing task targets`.

### Task 3: Generate an authorized, project-scoped draft

**Files:**

- Create `ProjectPlanningContextService` and tests.
- Modify `ProjectGenerationService`, controller, DTOs, and tests.

**Steps:**

1. Add failing tests proving only the project owner can plan a ticket in that project.
2. Snapshot the selected ticket plus ordered same-project tickets, criteria, dependencies, and assignment metadata.
3. Hash the deterministic snapshot and persist it before the AI call.
4. Add `POST /api/project-generation-runs/projects/{projectId}/tasks/{taskId}`.
5. Run focused tests and verify green.
6. Commit with `feat(planning): generate project scoped task plans`.

### Task 4: Confirm into the existing project atomically

**Files:**

- Modify `ProjectGenerationConfirmationService` and tests.

**Steps:**

1. Add failing tests for same-project child creation, selected-ticket refinement, idempotency, and stale-context rejection.
2. Recompute the context hash under the locked confirmation flow and reject changed context with HTTP 409.
3. Persist child tickets after the current project sequence, link them to the selected ticket, and reuse graph validation for criteria and dependencies.
4. Leave new-project confirmation behavior unchanged.
5. Run focused tests and verify green.
6. Commit with `feat(planning): confirm plans into existing projects`.

### Task 5: Add the explicit Plan with AI browser flow

**Files:**

- Modify frontend API tests/client, `App.test.jsx`, `App.jsx`, `BoardSection.jsx`, `TicketDetailPanel.jsx`, `DraftEditor.jsx`, and styles.

**Steps:**

1. Add failing UI tests for starting from a ticket, reviewing scoped context, and confirming child tickets into the current project.
2. Add `Plan with AI` to owner ticket details and a clear existing-project context banner in the Workshop.
3. Reuse the editable draft with honest copy: confirmation refines one ticket and adds child tickets; it does not create a second project.
4. Return successful contextual confirmations to the existing Board.
5. Run frontend tests and build, then commit with `feat(web): plan existing tickets with ai`.

### Task 6: Review, verify, and merge

1. Run all non-database Java tests and package with Java 21.
2. Run all AI-service tests, `npm test`, and `npm run build`.
3. Run a mocked-backend browser smoke for Plan with AI -> edit -> confirm -> existing Board.
4. Review ownership, prompt/context boundaries, stale confirmation, idempotency, lineage, and new-project regressions.
5. Fix every critical and important finding, repeat the gates, and fast-forward merge only from a clean worktree.

**Deferred:** Durable retrieval/retry/resume UI for interrupted runs is implemented on the next dedicated recovery branch so this branch stays focused on correct project-scoped planning and writes.
