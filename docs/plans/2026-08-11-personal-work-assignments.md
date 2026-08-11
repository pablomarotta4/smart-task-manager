# Personal Work and Project Assignments Implementation Plan

> **For Codex:** Execute task-by-task with witnessed red-green tests. Review and verify the complete branch before merging.

**Goal:** Give projects an explicit participant list, restrict assignment to those participants, and make My Work an authenticated user’s real assigned queue instead of a client-side aggregation of every owned project.

**Architecture:** Add a durable `project_memberships` relation that is backfilled with every existing owner. Owners alone manage participants, create/delete tickets, and change assignment. Participants can read and update tickets assigned to them through a dedicated principal-scoped My Work endpoint; project and backlog browsing remain owner-scoped. Spring owns every membership, authorization, and assignment decision. React consumes owner-scoped member controls on the Board and a single assignee-scoped My Work request.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Data JPA, PostgreSQL/Flyway, JUnit 5, Mockito, MockMvc, React 19, Vitest, Testing Library, Vite.

---

### Task 1: Persist explicit project participation

**Files:**

- Create: `src/main/resources/db/migration/V4__add_project_memberships.sql`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/model/ProjectMembership.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/repository/ProjectMembershipRepository.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectService.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectGenerationConfirmationService.java`
- Modify their service tests.

**Steps:**

1. Add failing tests proving manual and AI-confirmed projects create an owner membership.
2. Add the membership table with a unique project/user pair and backfill every existing project owner.
3. Persist owner membership in both project creation paths.
4. Run focused tests and verify green.
5. Commit with `feat(projects): persist project participation`.

### Task 2: Add owner-managed participant APIs and assignment rules

**Files:**

- Create membership request/response DTOs, service, controller, and tests.
- Modify `TaskService`, `TaskRepository`, and tests.

**Steps:**

1. Add failing tests proving only an owner can list/add/remove participants, inactive or unknown users are rejected, owners cannot be removed, and removal clears that user’s project assignments.
2. Add failing tests proving task create/update/assignment accepts only project participants and keeps assignment changes owner-only.
3. Implement `GET/POST/DELETE /api/projects/{projectId}/members` without exposing the global user directory.
4. Enforce membership at the task write boundary and allow an assignee to update only their own assigned ticket without reassigning it.
5. Run focused tests and verify green.
6. Commit with `feat(assignments): enforce project participation`.

### Task 3: Serve an authenticated personal queue

**Files:**

- Modify `TaskRepository`, planning detail repositories, `TaskService`, `TaskController`, and tests.

**Steps:**

1. Add failing tests for `GET /api/tasks/my-work`, proving the principal username is used and only that user’s assigned tasks are returned across projects.
2. Preserve project identity, estimates, acceptance criteria, and dependencies in the personal queue response.
3. Keep owner-scoped project/status endpoints unchanged.
4. Run focused tests and verify green.
5. Commit with `feat(tasks): add personal assignment queue`.

### Task 4: Extend the browser API client

**Files:**

- Modify `frontend/api.test.js`
- Modify `frontend/api.js`

**Steps:**

1. Add failing request-contract tests for member list/add/remove, assignment, and My Work.
2. Implement authenticated, encoded client methods.
3. Run the API tests and verify green.
4. Commit with `feat(web): add assignment api calls`.

### Task 5: Make Board participation and My Work usable

**Files:**

- Modify `frontend/App.test.jsx`, `frontend/App.jsx`, `ProjectDesk.jsx`, `TicketDetailPanel.jsx`, `TaskCreatePanel.jsx`, `MyWorkSection.jsx`, and `styles.css`.

**Steps:**

1. Add failing UI tests for adding/removing a participant, assigning a ticket to a participant, and loading My Work from one principal-scoped endpoint.
2. Add a People panel to the project desk and assignee controls to owner task forms.
3. Replace client-side all-project aggregation with `GET /api/tasks/my-work`.
4. Show assignee provenance and honest empty/error states in My Work.
5. Run frontend tests and build, then commit with `feat(web): add project assignments and personal queue`.

### Task 6: Review, verify, and merge

1. Run all non-database Java tests and package with Java 21.
2. Run `npm test`, `npm run build`, and all AI-service tests.
3. Run a mocked-backend browser smoke for participant management, assignment, and My Work.
4. Review authorization boundaries, assignment removal, data exposure, stale UI state, and migration safety.
5. Fix every critical and important finding, repeat the gates, and fast-forward merge only from a clean worktree.
