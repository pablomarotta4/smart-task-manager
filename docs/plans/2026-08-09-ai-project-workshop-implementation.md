# AI Project Workshop Implementation Plan

> **For Codex:** Execute this plan task-by-task. If an `executing-plans` skill is installed, use it; otherwise follow the steps manually.

**Goal:** Build a local React interface for signing in, generating an AI project draft, editing its tickets, reviewing quality feedback, and confirming the project.

**Architecture:** A Vite React SPA on port 3000 calls the existing Spring API on port 8080 through a small typed-by-convention fetch client. The application uses explicit view states and keeps the editable draft in React state; Spring remains the source of truth for authentication, generation, confirmation, and persistence.

**Tech Stack:** React 19, Vite, Vitest, React Testing Library, plain CSS, Lucide React, Spring Boot API.

**Files to Understand:**

- `src/main/java/com/pablomarotta/smart_task_manager/controller/AuthController.java` - login contract.
- `src/main/java/com/pablomarotta/smart_task_manager/controller/ProjectGenerationController.java` - draft and confirmation endpoints.
- `src/main/java/com/pablomarotta/smart_task_manager/dto/planning/PlanningTicketDraft.java` - editable ticket contract.
- `src/main/java/com/pablomarotta/smart_task_manager/config/CorsConfig.java` - allowed local frontend origins.
- `README.md` - current local runtime instructions.

---

### Task 1: Frontend toolchain and API client

**Files:**

- Modify: `package.json`
- Modify: `package-lock.json`
- Create: `index.html`
- Create: `vite.config.js`
- Create: `frontend/test/setup.js`
- Create: `frontend/api.test.js`
- Create: `frontend/api.js`

**Step 1: Write the failing API tests**

Test that login sends username/password, generation includes the bearer token and prompt, confirmation sends the edited draft to the run URL, and Spring error payloads become readable `ApiError` instances.

**Step 2: Run the focused test and verify RED**

Run: `npm test -- frontend/api.test.js`

Expected: FAIL because `frontend/api.js` does not exist.

**Step 3: Add the minimal Vite/Vitest toolchain and API client**

Configure scripts for `dev`, `build`, `test`, and `test:web`; set Vite to port 3000; implement `login`, `generateProject`, and `confirmProject` using injected or global `fetch`.

**Step 4: Run the focused test and verify GREEN**

Run: `npm test -- frontend/api.test.js`

Expected: all API client tests pass.

### Task 2: Authentication and prompt workspace

**Files:**

- Create: `frontend/App.test.jsx`
- Create: `frontend/App.jsx`
- Create: `frontend/main.jsx`

**Step 1: Write failing interaction tests**

Cover login, authenticated user display, prompt submission, loading copy, and a readable API error that preserves the prompt.

**Step 2: Run the focused test and verify RED**

Run: `npm test -- frontend/App.test.jsx`

Expected: FAIL because the application components do not exist.

**Step 3: Implement minimal authentication and generation states**

Use accessible labels and live regions, session storage for the token, functional state updates, and a submit handler that calls the API without request waterfalls.

**Step 4: Run the focused test and verify GREEN**

Run: `npm test -- frontend/App.test.jsx`

Expected: authentication and prompt tests pass.

### Task 3: Editable draft, quality report, and confirmation

**Files:**

- Modify: `frontend/App.test.jsx`
- Modify: `frontend/App.jsx`
- Create: `frontend/components/DraftEditor.jsx`
- Create: `frontend/components/QualityPanel.jsx`
- Create: `frontend/components/TicketEditor.jsx`

**Step 1: Add failing draft workflow tests**

Verify the returned project and quality issues render, editing a ticket title updates state, confirmation sends the edited draft, and the success state shows project/task identifiers.

**Step 2: Run the focused test and verify RED**

Run: `npm test -- frontend/App.test.jsx`

Expected: FAIL because the draft workflow is absent.

**Step 3: Implement the draft workflow**

Render all quality evidence, editable project text, assumptions, risks, ticket fields, acceptance criteria, and read-only dependency IDs. Disable confirmation during requests and preserve backend idempotency semantics.

**Step 4: Run the focused test and verify GREEN**

Run: `npm test -- frontend/App.test.jsx`

Expected: the complete interaction suite passes.

### Task 4: Editorial workshop styling

**Files:**

- Create: `frontend/styles.css`
- Modify: `frontend/App.jsx`
- Modify: `frontend/components/DraftEditor.jsx`
- Modify: `frontend/components/QualityPanel.jsx`
- Modify: `frontend/components/TicketEditor.jsx`

**Step 1: Add semantic rendering assertions where behavior matters**

Assert accessible headings, form names, quality status text, and busy/disabled states before styling.

**Step 2: Run the tests and verify RED for missing semantics**

Run: `npm test`

Expected: the new semantic assertions fail.

**Step 3: Implement the visual system and semantics**

Add CSS variables, typography, paper grid, responsive layouts, high-contrast focus states, reduced-motion handling, ticket sequencing, and quality score treatments without adding runtime styling dependencies.

**Step 4: Run tests and production build**

Run: `npm test && npm run build`

Expected: tests and build pass without warnings or errors.

### Task 5: Documentation and real-browser verification

**Files:**

- Modify: `README.md`

**Step 1: Document local frontend startup and the test flow**

Add `npm install`, `npm run dev`, the port 3000 URL, and the native Ollama recommendation for Apple Silicon.

**Step 2: Run full automated verification**

Run: `npm test && npm run build && git diff --check`

Expected: all commands exit zero.

**Step 3: Exercise the real local stack with Playwright**

Open `http://127.0.0.1:3000`, log in with the local test account, generate a project, edit one field, confirm it, inspect browser console errors, and capture desktop/mobile screenshots.

**Step 4: Review and commit**

Review the branch diff for accessibility, credential leakage, API contract drift, and unrelated changes, then create a focused Conventional Commit.
