# Execution Workspace Frontend Implementation Plan

> **For Codex:** Execute this plan task-by-task. If an `executing-plans` skill is installed, use it; otherwise follow the steps manually.

**Goal:** Add Board, My Work, Account, ticket-editor, project-settings, and AI follow-up planning surfaces to the authenticated React frontend.

**Architecture:** Keep authentication and view orchestration in `App.jsx`, place each visual surface in a focused component, and use the existing Spring task/project contracts. Project updates and existing-project planning stay explicitly unavailable until backend endpoints exist.

**Tech Stack:** React 19, Vite, Vitest, React Testing Library, plain CSS, Spring Boot REST API.

**Files to Understand:**

- `frontend/App.jsx` - authenticated navigation and shared project state.
- `frontend/api.js` - HTTP request boundary.
- `frontend/components/ProjectsSection.jsx` - current project presentation language.
- `frontend/styles.css` - editorial design system and responsive breakpoints.
- `src/main/java/com/pablomarotta/smart_task_manager/controller/TaskController.java` - supported ticket mutations.

---

### Task 1: Add task execution API methods

**Files:**

- Modify: `frontend/api.test.js`
- Modify: `frontend/api.js`

**Steps:**

1. Add failing tests for full task update and status update requests.
2. Run `npm test -- frontend/api.test.js` and verify the methods are missing.
3. Add `updateTask` and `updateTaskStatus` with encoded identifiers and bearer authentication.
4. Run the focused tests and verify green.
5. Commit with `feat(web): add task execution API client`.

### Task 2: Build the project Board and ticket editor

**Files:**

- Create: `frontend/components/BoardSection.jsx`
- Create: `frontend/components/TicketDetailPanel.jsx`
- Create: `frontend/components/ExecutionViews.test.jsx`
- Modify: `frontend/App.jsx`
- Modify: `frontend/App.test.jsx`

**Steps:**

1. Add failing component tests for status lanes, project selection, ticket opening, form editing, and save callbacks.
2. Add a failing App interaction test for Board navigation, project/backlog loading, and a persisted ticket update.
3. Run focused tests and verify red for missing views and behavior.
4. Implement Board loading/orchestration, semantic lanes, the ticket editor, and operational-field merging.
5. Run focused tests and verify green.
6. Commit with `feat(web): add project execution board`.

### Task 3: Build My Work aggregation

**Files:**

- Create: `frontend/components/MyWorkSection.jsx`
- Modify: `frontend/components/ExecutionViews.test.jsx`
- Modify: `frontend/App.jsx`
- Modify: `frontend/App.test.jsx`

**Steps:**

1. Add failing tests for cross-project aggregation, blocked work, due-soon work, and ticket selection.
2. Add a failing App test proving project backlogs load concurrently and retain project identity.
3. Run focused tests and verify red.
4. Implement concurrent backlog loading and the My Work view.
5. Run focused tests and verify green.
6. Commit with `feat(web): add my work dashboard`.

### Task 4: Add Account and contextual project tools

**Files:**

- Create: `frontend/components/AccountSection.jsx`
- Create: `frontend/components/ProjectDesk.jsx`
- Modify: `frontend/components/ExecutionViews.test.jsx`
- Modify: `frontend/App.jsx`

**Steps:**

1. Add failing tests for account identity, sign-out, AI follow-up, and honest project-settings limitations.
2. Run focused tests and verify red.
3. Implement Account and Project Desk surfaces and wire the AI follow-up action to a prefilled new-project brief.
4. Run focused tests and verify green.
5. Commit with `feat(web): add account and project desk views`.

### Task 5: Style and verify the complete workspace

**Files:**

- Modify: `frontend/styles.css`
- Modify: `README.md`

**Steps:**

1. Add the responsive operations-desk layout, board lanes, ticket sheet, My Work hierarchy, account folio, focus states, and compact-screen behavior.
2. Run `npm test`, `npm run build`, and `git diff --check`.
3. Start the local stack and exercise login, Board, ticket editing, My Work, AI follow-up, Account, and mobile layouts in a real browser.
4. Review the complete branch diff and fix all important findings.
5. Commit with `feat(web): finish execution workspace views`.
