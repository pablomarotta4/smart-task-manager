# Manual Project and Task Lifecycle Implementation Plan

> **For Codex:** Execute this plan task-by-task with witnessed red-green tests. Review and verify the complete branch before merging.

**Goal:** Let an authenticated project owner create, edit, and delete projects and tickets without invoking AI, while preserving ownership enforcement and correct task lifecycle metadata.

**Architecture:** Extend the existing REST/service/repository flow rather than introducing a parallel manual domain. Spring remains the write boundary. Manual task creation stores user-authored fields directly and does not depend on Ollama. Project deletion relies on the existing database cascades. The React client exposes focused forms and explicit confirmation states, then reconciles local project/task state from API responses.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Data JPA, JUnit 5, Mockito, MockMvc, React 19, Vitest, Testing Library, Vite.

---

### Task 1: Complete owner-scoped project writes

**Files:**

- Modify: `src/test/java/com/pablomarotta/smart_task_manager/service/ProjectServiceTest.java`
- Modify: `src/test/java/com/pablomarotta/smart_task_manager/controller/ProjectControllerTest.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/dto/ProjectRequest.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectService.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/controller/ProjectController.java`

**Steps:**

1. Add failing tests proving manual creation persists the objective, update changes only an owned project, delete removes only an owned project, and inaccessible IDs return not-found without writes.
2. Add controller tests proving `PUT /api/projects/{id}` and `DELETE /api/projects/{id}` pass the authenticated username.
3. Run the focused tests and witness red for the missing project lifecycle APIs.
4. Add objective validation/mapping plus owner-scoped update and delete service methods and endpoints.
5. Run focused tests and verify green.
6. Commit with `feat(projects): add manual project lifecycle`.

### Task 2: Make the task lifecycle independent from AI

**Files:**

- Modify: `src/test/java/com/pablomarotta/smart_task_manager/service/TaskServiceTest.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/dto/TaskRequest.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/TaskService.java`

**Steps:**

1. Add failing tests proving manual task creation does not call AI, stores the owner as creator, and defaults a missing position to the end of the backlog.
2. Add failing tests proving a full task update can clear due date/category/description, rejects a project move, and synchronizes `completedAt` when entering or leaving `DONE`.
3. Add happy-path deletion coverage in addition to the existing foreign-task denial tests.
4. Run focused tests and witness red.
5. Remove the AI dependency from ordinary task creation, apply full replacement semantics to editable fields, and centralize completion metadata updates.
6. Run focused tests and verify green.
7. Commit with `feat(tasks): complete manual task lifecycle`.

### Task 3: Extend and verify the browser API client

**Files:**

- Modify: `frontend/api.test.js`
- Modify: `frontend/api.js`

**Steps:**

1. Add failing request-contract tests for create/update/delete project and create/delete task calls.
2. Implement the API client methods with encoded identifiers and authenticated JSON requests.
3. Run `npm test -- frontend/api.test.js` and verify green.
4. Commit with `feat(web): add manual lifecycle api calls`.

### Task 4: Add project creation and settings flows

**Files:**

- Modify: `frontend/App.test.jsx`
- Modify: `frontend/App.jsx`
- Modify: `frontend/components/ProjectsSection.jsx`
- Modify: `frontend/components/ProjectDesk.jsx`
- Create: `frontend/components/ProjectCreatePanel.jsx`
- Modify: `frontend/styles.css`

**Steps:**

1. Add failing UI tests for creating a project without AI, editing its name/objective, and deleting it only after explicit confirmation.
2. Add App handlers that call the new API methods and reconcile `projects`, `selectedProject`, and `projectTasks`.
3. Add an accessible manual-create panel in Projects and editable project settings in the Board desk.
4. Keep request failures distinct from empty states and preserve the selected project on failed mutations.
5. Run focused frontend tests and verify green.
6. Commit with `feat(web): add manual project controls`.

### Task 5: Add task creation and deletion flows

**Files:**

- Modify: `frontend/App.test.jsx`
- Modify: `frontend/App.jsx`
- Modify: `frontend/components/BoardSection.jsx`
- Modify: `frontend/components/TicketDetailPanel.jsx`
- Create: `frontend/components/TaskCreatePanel.jsx`
- Modify: `frontend/styles.css`

**Steps:**

1. Add failing UI tests for adding a manual ticket, clearing its due date through edit, and deleting it only after explicit confirmation.
2. Add App handlers for task creation/deletion and keep project task counts synchronized.
3. Add an accessible creation panel to the board and a destructive confirmation state to ticket details.
4. Run focused frontend tests and verify green.
5. Commit with `feat(web): add manual ticket controls`.

### Task 6: Review, verify, and merge

1. Run all non-database Java tests with Java 21 and package the backend.
2. Run `npm test`, `npm run build`, and `ai-service/.venv/bin/pytest ai-service/tests`.
3. Run a mocked-backend browser smoke test for project and ticket manual flows if the local database remains unavailable.
4. Review `main..feature/manual-project-task-lifecycle` for ownership regressions, destructive-action mistakes, stale UI state, and API incompatibilities.
5. Fix every critical and important finding, repeat the gates, and fast-forward merge only from a clean worktree.
