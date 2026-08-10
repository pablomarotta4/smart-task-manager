# Project Browser Implementation Plan

> **For Codex:** Execute this plan task-by-task. If an `executing-plans` skill is installed, use it; otherwise follow the steps manually.

**Goal:** Add a read-only Projects section that lists persisted projects and displays one selected project's complete ticket backlog.

**Architecture:** Extend the existing Spring read contracts additively and keep mapping inside read-only service transactions. React uses the existing API client and explicit view state, loading project summaries first and ticket detail only after selection.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, JUnit 5, Mockito, React 19, Vite, Vitest, React Testing Library, plain CSS.

**Files to Understand:**

- `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectService.java` - currently maps a lazy owner outside a transaction.
- `src/main/java/com/pablomarotta/smart_task_manager/service/TaskService.java` - maps the task read contract.
- `src/main/java/com/pablomarotta/smart_task_manager/repository/TaskRepository.java` - project-scoped task queries.
- `frontend/api.js` - authenticated HTTP boundary.
- `frontend/App.jsx` - authentication and workshop view state.
- `frontend/styles.css` - existing editorial design system.

---

### Task 1: Repair and enrich project summaries

**Files:**

- Modify: `src/test/java/com/pablomarotta/smart_task_manager/service/ProjectServiceTest.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/dto/ProjectResponse.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/repository/TaskRepository.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectService.java`

**Step 1:** Add failing service tests asserting newest-first summaries include objective and task count, and use project task-count results.

**Step 2:** Run `mvn -Dtest=ProjectServiceTest test` and verify RED because the response/count query does not exist.

**Step 3:** Add an additive `objective` and `taskCount` response contract, a grouped count projection, and `@Transactional(readOnly = true)` project reads.

**Step 4:** Run the focused test and verify GREEN.

**Step 5:** Commit with `fix(projects): make project summaries readable`.

### Task 2: Return planning details for project tickets

**Files:**

- Create: `src/test/java/com/pablomarotta/smart_task_manager/service/TaskServiceTest.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/dto/TaskResponse.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/repository/TaskRepository.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/repository/TaskAcceptanceCriterionRepository.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/repository/TaskDependencyRepository.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/TaskService.java`

**Step 1:** Add a failing project-ticket service test asserting position order, estimate, AI summary, acceptance criteria, and dependency client IDs.

**Step 2:** Run `mvn -Dtest=TaskServiceTest test` and verify RED for missing response fields and repository methods.

**Step 3:** Add additive task fields and project-bounded repository queries; assemble the response with three bounded reads inside a read-only transaction.

**Step 4:** Run the focused test and verify GREEN.

**Step 5:** Commit with `feat(projects): expose project ticket details`.

### Task 3: Add frontend project data client

**Files:**

- Modify: `frontend/api.test.js`
- Modify: `frontend/api.js`

**Step 1:** Add failing tests for authenticated `GET /api/projects` and `GET /api/tasks/project/{id}` calls.

**Step 2:** Run `npm test -- frontend/api.test.js` and verify RED because the methods do not exist.

**Step 3:** Generalize the request helper's HTTP method and implement `getProjects` and `getProjectTasks`.

**Step 4:** Run the focused test and verify GREEN.

**Step 5:** Commit with `feat(web): add project browser API client`.

### Task 4: Build the Projects section

**Files:**

- Modify: `frontend/App.test.jsx`
- Modify: `frontend/App.jsx`
- Create: `frontend/components/ProjectsSection.jsx`

**Step 1:** Add failing interaction tests for navigation, summary loading, project selection, ticket details, empty state, API errors, and the post-confirmation “View project” action.

**Step 2:** Run `npm test -- frontend/App.test.jsx` and verify RED because Projects navigation and content do not exist.

**Step 3:** Add explicit Workshop/Projects view state, load summaries on entry, load tickets on selection, and clear the session on HTTP 401.

**Step 4:** Run the focused test and verify GREEN.

**Step 5:** Commit with `feat(web): add projects and backlog browser`.

### Task 5: Style and verify the complete flow

**Files:**

- Modify: `frontend/styles.css`
- Modify: `README.md`

**Step 1:** Add semantic assertions for selected navigation, loading status, project buttons, ticket headings, and accessible metadata.

**Step 2:** Run `npm test` and verify the new assertions fail before semantic/styling work.

**Step 3:** Add the responsive editorial project index/detail layout, focus states, long-list content visibility, empty/error treatments, and documentation.

**Step 4:** Run `npm test`, `npm run build`, focused Maven tests, and `git diff --check`.

**Step 5:** Exercise login → Projects → project #20 → tickets and confirmation → View project in a real browser at desktop and mobile widths.

**Step 6:** Review the complete branch diff and commit with `feat(web): finish project browser experience`.
