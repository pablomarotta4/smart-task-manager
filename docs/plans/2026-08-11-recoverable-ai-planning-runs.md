# Recoverable AI Planning Runs Implementation Plan

> **For Codex:** Execute task-by-task with witnessed red-green tests. Review and verify the complete branch before merging.

**Goal:** Let an authenticated user find recent AI planning runs, reopen a persisted draft after a browser refresh, and safely retry failed or abandoned processing runs without creating duplicate projects or tickets.

**Architecture:** Spring remains the source of truth for run lifecycle and authorization. Run summaries expose only the requester’s records; run detail reconstructs the persisted draft and quality report. Retry atomically claims an eligible run, refreshes existing-project context, increments its attempt count, and reruns AI outside the claim transaction. The same run ID and explicit confirmation boundary are preserved. The frontend renders recent runs in the Workshop and restores the correct new-project or existing-ticket editing context.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Data JPA, PostgreSQL/Flyway, JUnit 5, Mockito, MockMvc, React 19, Vitest, Testing Library, Vite.

---

### Task 1: Persist retry attempts and define recovery contracts

**Files:**

- Create `V6__add_generation_attempt_count.sql`.
- Modify `ProjectGenerationRun`.
- Add run summary/detail DTOs and mapping tests.

**Steps:**

1. Add failing tests for requester-safe summaries and complete draft reconstruction.
2. Persist `attempt_count` with legacy runs backfilled to one attempt.
3. Return mode, target labels, status, safe error code, timestamps, and attempt count in summaries.
4. Return the persisted draft and quality payload only from authorized run detail.
5. Run focused tests and commit with `feat(planning): expose recoverable run history`.

### Task 2: Add owner-scoped retrieval endpoints

**Files:**

- Modify `ProjectGenerationRunRepository`.
- Add `ProjectGenerationRunQueryService` and tests.
- Modify `ProjectGenerationController` and tests.

**Steps:**

1. Add failing service and controller tests for recent-run ordering, detail retrieval, not-found behavior, and cross-user isolation.
2. Add `GET /api/project-generation-runs` for the requester’s ten most recent runs.
3. Add `GET /api/project-generation-runs/{runId}` for one requester-owned run.
4. Keep provider failures and stored implementation details out of responses.
5. Run focused tests and commit with `feat(planning): retrieve personal generation runs`.

### Task 3: Retry failed or abandoned runs safely

**Files:**

- Modify `ProjectGenerationService`, controller, and tests.

**Steps:**

1. Add failing tests for retry authorization, status eligibility, attempt increments, context refresh, bounded stale-processing recovery, and duplicate retry rejection.
2. Claim a retry under a short pessimistic-lock transaction; allow `FAILED` or `PROCESSING` older than two minutes.
3. Refresh the selected project/task context and hash for existing-task runs before the AI call.
4. Clear the safe error code, increment attempts, and reuse the original run ID and prompt.
5. Mark unexpected generation failures explicitly instead of leaving a permanent `PROCESSING` record.
6. Add `POST /api/project-generation-runs/{runId}/retry`, run focused tests, and commit with `feat(planning): retry recoverable generation runs`.

### Task 4: Restore runs in the Workshop

**Files:**

- Modify frontend API client/tests, `App.jsx`, `App.test.jsx`, and styles.
- Add `RecentPlanningRuns.jsx`.

**Steps:**

1. Add failing API and UI tests for history loading, draft restoration, failed retry, existing-ticket context restoration, and error states.
2. Load recent runs after login and on an already-authenticated refresh without blocking the Workshop.
3. Let users resume `DRAFT_READY`, retry eligible `FAILED` or stale `PROCESSING`, and open a confirmed project.
4. Preserve the prompt and planning-target labels when restoring a run.
5. Refresh history after generation, retry, and confirmation.
6. Run frontend tests/build and commit with `feat(web): restore recent ai planning runs`.

### Task 5: Review, verify, and merge

1. Run all non-database Java tests and package with Java 21.
2. Run all AI-service tests and static checks, frontend tests, and frontend production build.
3. Run a browser smoke that refreshes, resumes a draft, retries a failed run, and reaches confirmation with no console errors.
4. Review authorization, stale-processing threshold, retry transaction boundaries, context refresh, JSON reconstruction, idempotency, and existing flows.
5. Fix every critical and important finding, repeat the gates, and fast-forward merge only from a clean worktree.

**Deferred:** Asynchronous background workers and live polling. Generation remains request/response in this local product; recovery covers browser loss, completed-but-disconnected responses, explicit failures, and processing records abandoned by a restart.
