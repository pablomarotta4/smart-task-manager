# Product Capabilities Epic Implementation Plan

> **For Codex:** Execute wave by wave with witnessed red-green tests. Do not parallelize shared migrations, `frontend/App.jsx`, or `frontend/styles.css`. After each wave, integrate, run its gate, review, and commit before assigning dependent work.

**Goal:** Implement and verify collaboration, activity, notifications, discovery, account recovery, recurring work, templates, attachments, analytics, and the consolidated AI boundary described in `docs/plans/2026-08-12-product-capabilities-epic-design.md`.

**Architecture:** A Spring/PostgreSQL modular monolith owns deterministic state and writes. React uses focused feature modules. FastAPI is the only AI provider boundary. Transactional activity and in-app notifications accompany business mutations; retryable email uses an outbox. Local adapters keep development self-contained.

**Tech stack:** Java 21, Spring Boot 3.4.1, Spring Security, Spring Data JPA, PostgreSQL/Flyway, Testcontainers, JUnit 5/Mockito/MockMvc, React 19, Vite/Vitest/Testing Library, Python 3.12, FastAPI/LangGraph/Pydantic/Pytest, Docker Compose, Playwright.

---

## Wave 0: Safe execution foundation

### Task 0.1: Isolate all database integration tests

**Dependencies:** None

**Files:**

- Modify: `pom.xml`
- Create: `src/test/java/com/pablomarotta/smart_task_manager/integration/PostgresIntegrationTest.java`
- Create: `src/test/resources/application-integration.yaml`
- Modify: `src/test/java/com/pablomarotta/smart_task_manager/integration/FullFlowIntegrationTest.java`
- Modify: `src/test/java/com/pablomarotta/smart_task_manager/integration/ProjectGenerationIntegrationTest.java`
- Modify: `src/test/java/com/pablomarotta/smart_task_manager/integration/TaskAIIntegrationTest.java`
- Modify: `src/test/java/com/pablomarotta/smart_task_manager/integration/TaskFlowIntegrationTest.java`
- Create: `src/test/java/com/pablomarotta/smart_task_manager/integration/FlywayMigrationIntegrationTest.java`

**Steps:**

1. Add a failing guard test proving integration tests reject a non-container JDBC URL.
2. Add Testcontainers PostgreSQL and the shared integration-test base/profile.
3. Move every destructive integration test onto the container datasource.
4. Add empty-database and sequential Flyway migration tests.
5. Run unit tests separately from tagged integration tests, then run the integration suite.

**Acceptance criteria:** No integration test reads, truncates, or deletes from the configured developer PostgreSQL database; a disposable PostgreSQL container applies every migration and all existing integration flows pass.

### Task 0.2: Establish CI and correlation IDs

**Dependencies:** Task 0.1

**Files:**

- Create: `.github/workflows/ci.yml`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/config/CorrelationIdFilter.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/config/SecurityConfig.java`
- Create: `src/test/java/com/pablomarotta/smart_task_manager/config/CorrelationIdFilterTest.java`
- Modify: `ai-service/src/smart_task_ai/api.py`
- Modify: `ai-service/tests/test_api.py`
- Modify: `docker-compose.yml`

**Steps:**

1. Add failing Spring and FastAPI tests for accepted/generated correlation IDs and response propagation.
2. Implement sanitized bounded correlation-ID propagation.
3. Add independent frontend, backend-unit, backend-integration, AI, and build jobs.
4. Keep live Ollama evaluation manual/scheduled and use mocked model behavior in PR CI.
5. Verify workflow commands locally.

**Acceptance criteria:** CI contains independently diagnosable jobs; backend integration uses Testcontainers; no CI job requires a live Ollama instance; correlation IDs propagate without accepting unsafe header values.

---

## Wave 1: Authorization and frontend modularity

### Task 1.1: Add project roles and a centralized access policy

**Dependencies:** Wave 0

**Files:**

- Create: `src/main/resources/db/migration/V8__add_project_roles.sql`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/model/ProjectRole.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/model/ProjectMembership.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/repository/ProjectMembershipRepository.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectAccessPolicy.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectService.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/TaskService.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectMembershipService.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectPlanningContextService.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectGenerationConfirmationService.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectGenerationRunQueryService.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/dto/ProjectResponse.java`
- Modify matching service/controller/security tests.

**Steps:**

1. Add a parameterized failing role/capability matrix covering owner, manager, member, and nonmember.
2. Migrate existing owners to `OWNER`, other memberships to `MEMBER`, and enforce one owner membership per project.
3. Implement `requireMember`, `requireManager`, `requireOwner`, `requireTaskViewer`, and `requireTaskEditor`.
4. Replace owner-only and ad hoc membership checks across project, task, membership, and planning flows.
5. Return `currentUserRole` and list all projects visible to the principal.
6. Correct manual task creation to record the authenticated actor as creator.

**Acceptance criteria:** All project-aware operations use one policy; managers and members receive only their defined capabilities; nonmembers receive 404; every project creation path atomically creates one owner membership.

### Task 1.2: Make frontend permissions explicit and split integration hotspots

**Dependencies:** Task 1.1

**Files:**

- Create: `frontend/lib/projectPermissions.js`
- Create: `frontend/lib/projectPermissions.test.js`
- Create: `frontend/hooks/useProjectWorkspace.js`
- Create: `frontend/hooks/useProjectWorkspace.test.jsx`
- Modify: `frontend/App.jsx`
- Modify: `frontend/App.test.jsx`
- Modify: `frontend/components/BoardSection.jsx`
- Modify: `frontend/components/ProjectDesk.jsx`
- Modify: `frontend/components/TicketDetailPanel.jsx`
- Modify: `frontend/components/TaskCreatePanel.jsx`
- Modify: `frontend/styles.css`

**Steps:**

1. Add failing tests proving controls derive from `currentUserRole` and never default to management access.
2. Extract project workspace loading/mutation state from `App.jsx` into a focused hook.
3. Pass explicit permissions into ticket and project components.
4. Add honest 403/404/empty/loading rendering.
5. Run all frontend tests and build.

**Acceptance criteria:** No management component defaults permission to true; owner, manager, and member controls match the backend matrix; existing Board, My Work, Projects, Account, and AI planning journeys remain green.

---

## Wave 2: Identity and delivery foundation

### Task 2.1: Normalize identity and add single-use action links

**Dependencies:** Wave 1

**Files:**

- Create: `src/main/resources/db/migration/V9__add_account_actions_and_email_outbox.sql`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/model/User.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/model/AccountActionRequest.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/model/AccountActionPurpose.java`
- Create repositories for account actions and email outbox.
- Create: `src/main/java/com/pablomarotta/smart_task_manager/security/ActionTokenCodec.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/security/JwtTokenProvider.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/security/JwtAuthenticationFilter.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/UserService.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/RefreshTokenService.java`
- Modify matching security/service/integration tests.

**Steps:**

1. Add failing migration tests for normalized-email collision detection and verified backfill.
2. Add failing codec tests for purpose, expiry, version, tampering, and raw-token absence.
3. Make usernames immutable and add normalized email, verification timestamp, and auth version.
4. Implement row-locked, single-use account actions.
5. Add bulk refresh-token revocation and auth-version JWT validation.

**Acceptance criteria:** Existing users remain verified; case-insensitive email identity is unique; action tokens are single-use and never stored raw; password changes invalidate refresh and access sessions.

### Task 2.2: Implement verification, password recovery, email outbox, and rate limiting

**Dependencies:** Task 2.1

**Files:**

- Modify: `pom.xml`
- Create mail/outbox models and services under `model/`, `repository/`, and `service/`.
- Create: `src/main/java/com/pablomarotta/smart_task_manager/service/EmailDelivery.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/service/SmtpEmailDelivery.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/service/AccountActionService.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/security/AuthRateLimiter.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/security/InMemoryAuthRateLimiter.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/controller/AuthController.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/config/SecurityConfig.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `docker-compose.yml`
- Create/modify matching controller/service/security/integration tests.

**Steps:**

1. Add failing tests for non-enumerating 202 responses, verification, reset, expiry, resend invalidation, and session revocation.
2. Add failing fake-clock rate-limit tests for IP and normalized identity buckets.
3. Implement verification and recovery endpoints with no account enumeration.
4. Enqueue outbox references transactionally and dispatch with bounded retry outside transactions.
5. Configure Mailpit locally and a fake delivery adapter in tests.
6. Apply rate limits before expensive or side-effecting work and return `429` plus `Retry-After`.

**Acceptance criteria:** Verification and reset flows work end to end with Mailpit; email failure does not roll back the originating business action; retries do not duplicate actions; rate-limit caches are bounded and trusted proxy spoofing is impossible by default.

### Task 2.3: Add account recovery and verification views

**Dependencies:** Task 2.2

**Files:**

- Modify: `frontend/api.js`
- Modify: `frontend/api.test.js`
- Create: `frontend/components/ForgotPasswordView.jsx`
- Create: `frontend/components/ResetPasswordView.jsx`
- Create: `frontend/components/VerifyEmailView.jsx`
- Modify: `frontend/components/AccountSection.jsx`
- Modify: `frontend/App.jsx`
- Modify: `frontend/App.test.jsx`
- Modify: `frontend/styles.css`

**Steps:**

1. Add failing request-contract and component tests.
2. Implement fragment-token parsing and POST-body confirmation.
3. Preserve generic recovery messaging and return-to-login behavior.
4. Expose verification state and resend action in Account.
5. Run frontend tests and build.

**Acceptance criteria:** Tokens never enter query strings; recovery UI does not disclose account existence; expired/used/error/success states are explicit and accessible.

---

## Wave 3: Invitations and membership roles

### Task 3.1: Implement the invitation state machine

**Dependencies:** Wave 2

**Files:**

- Create: `src/main/resources/db/migration/V10__add_project_invitations.sql`
- Create invitation model, enum, repository, DTOs, service, and controller.
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectMembershipService.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/controller/ProjectMembershipController.java`
- Modify matching unit, MockMvc, security, and integration tests.

**Steps:**

1. Add failing state-transition, permission, expiry, revoke, decline, and concurrent-acceptance tests.
2. Persist normalized target email and stored role without raw tokens.
3. Enforce verified-email matching and role-from-server acceptance.
4. Lock invitation acceptance, create membership atomically, and make retries idempotent.
5. Enqueue invitation email and accepted-notice outbox records.
6. Add owner-only role changes and manager limitations.

**Acceptance criteria:** A forged project/role/user ID cannot alter invitation outcomes; exactly one concurrent acceptance creates membership; managers cannot grant manager access; owners cannot be removed or demoted.

### Task 3.2: Implement invitation and People UI

**Dependencies:** Task 3.1

**Files:**

- Modify: `frontend/api.js`
- Modify: `frontend/api.test.js`
- Create: `frontend/components/InvitationView.jsx`
- Create: `frontend/components/ProjectPeoplePanel.jsx`
- Create matching component tests.
- Modify: `frontend/components/ProjectDesk.jsx`
- Modify: `frontend/App.jsx`
- Modify: `frontend/App.test.jsx`
- Modify: `frontend/styles.css`

**Steps:**

1. Add failing tests for invite, copy link, revoke, accept, decline, login return, and role controls.
2. Replace direct username addition with email invitations while retaining existing member management.
3. Preserve invitation fragments across login and registration.
4. Render role badges, pending state, expiry, and permission-aware actions.
5. Run frontend tests and build.

**Acceptance criteria:** A new user can register, verify, return to the invitation, accept it, and enter the project; controls precisely match role permissions.

---

## Wave 4: Activity and comments

### Task 4.1: Add immutable activity capture

**Dependencies:** Wave 3

**Files:**

- Create: `src/main/resources/db/migration/V11__add_task_activity_and_comments.sql`
- Create activity model, enum, repository, DTOs, service, and controller.
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/TaskService.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectMembershipService.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectGenerationConfirmationService.java`
- Modify matching tests.

**Steps:**

1. Add failing atomicity tests: successful mutations create typed activity; failed mutations create none.
2. Implement append-only typed activity with whitelisted payloads and actor/title snapshots.
3. Record task, assignment, membership, label-ready, and AI-confirmation changes.
4. Add stable `(occurredAt,id)` cursor pagination.
5. Verify deletion leaves understandable history without retaining forbidden content.

**Acceptance criteria:** Activity is atomic, immutable through public APIs, stable under identical timestamps, and contains no raw comment, prompt, email, attachment, or token data.

### Task 4.2: Add comments and mentions

**Dependencies:** Task 4.1

**Files:**

- Create comment model, repository, DTOs, service, and controller using the V11 schema.
- Create: `src/main/java/com/pablomarotta/smart_task_manager/service/MentionResolver.java`
- Create matching unit, MockMvc, security, and integration tests.

**Steps:**

1. Add failing tests for membership access, 5,000-character validation, ownership, moderation, and soft deletion.
2. Implement comment CRUD and typed activity in one transaction.
3. Resolve exact project-member username mentions without exposing arbitrary users.
4. Return cursor-paginated comments without deleted body content.

**Acceptance criteria:** Members can participate; only authors or permitted moderators can alter comments; cross-project access is hidden; mentions resolve only current members.

### Task 4.3: Add ticket Comments and Activity tabs

**Dependencies:** Tasks 4.1 and 4.2

**Files:**

- Modify: `frontend/api.js`
- Modify: `frontend/api.test.js`
- Create: `frontend/components/TicketComments.jsx`
- Create: `frontend/components/TicketActivity.jsx`
- Create matching tests.
- Modify: `frontend/components/TicketDetailPanel.jsx`
- Modify: `frontend/App.test.jsx`
- Modify: `frontend/styles.css`

**Steps:**

1. Add failing UI/API tests for pagination, add/edit/delete, moderation, and empty/error states.
2. Add Details, Comments, and Activity tabs.
3. Render safe typed activity descriptions rather than arbitrary server HTML.
4. Run frontend tests and build.

**Acceptance criteria:** Ticket discussions and history are usable by keyboard, pagination remains stable, and permission failures roll back optimistic UI cleanly.

---

## Wave 5: Notifications

### Task 5.1: Implement notification inbox, preferences, and due scheduler

**Dependencies:** Wave 4

**Files:**

- Create: `src/main/resources/db/migration/V12__add_notifications.sql`
- Create notification/preference models, enums, repositories, DTOs, service, and controller.
- Create: `src/main/java/com/pablomarotta/smart_task_manager/service/DueNotificationScheduler.java`
- Modify task/comment/invitation services to materialize notifications transactionally.
- Create matching unit, MockMvc, security, concurrency, and integration tests.

**Steps:**

1. Add failing tests for assignment, mention, comment, due-soon, overdue, accepted-invite, preferences, read state, and deduplication.
2. Implement recipient-only inbox and preference mutations.
3. Create event notifications in the originating transaction using activity-based idempotency keys.
4. Implement a bounded due scheduler with deterministic recipient/date/type keys and a fake clock.
5. Queue optional email only when preferences permit.

**Acceptance criteria:** Failed business mutations create no notification; scheduler retries and multiple instances do not duplicate; deleted resources render historical non-clickable notices; users can never read another user's notification.

### Task 5.2: Add notification frontend

**Dependencies:** Task 5.1

**Files:**

- Modify: `frontend/api.js`
- Modify: `frontend/api.test.js`
- Create: `frontend/hooks/useNotifications.js`
- Create: `frontend/components/NotificationInbox.jsx`
- Create: `frontend/components/NotificationPreferences.jsx`
- Create matching tests.
- Modify: `frontend/components/AccountSection.jsx`
- Modify: `frontend/App.jsx`
- Modify: `frontend/App.test.jsx`
- Modify: `frontend/styles.css`

**Steps:**

1. Add failing tests for unread count, All/Unread filters, read/read-all, preferences, and deep links.
2. Add a topbar bell and inbox destination.
3. Keep counts synchronized after reads and incoming refreshes.
4. Render inaccessible/deleted resource notices without broken navigation.
5. Run frontend tests and build.

**Acceptance criteria:** Unread state is correct after individual and bulk changes; deep links open accessible tasks; all loading/empty/error states are distinct and accessible.

---

## Wave 6: Labels, global search, and filters

### Task 6.1: Migrate and implement project-scoped labels

**Dependencies:** Wave 5

**Files:**

- Create: `src/main/resources/db/migration/V13__scope_labels_to_projects.sql`
- Create label model, repository, DTOs, service, and controller.
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/model/Task.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/dto/TaskResponse.java`
- Modify task mapping and matching tests.

**Steps:**

1. Add migration fixtures proving existing label/task/project combinations migrate without cross-project collisions.
2. Enforce case-insensitive `(project,name)` uniqueness and color/name validation.
3. Implement project label catalog CRUD and atomic task label replacement.
4. Reject every cross-project label assignment transactionally.
5. Record typed activity for label changes.

**Acceptance criteria:** Two projects can each use `Bug`; case variants cannot coexist inside one project; labels never cross project boundaries; catalog and assignment permissions follow the role matrix.

### Task 6.2: Implement paginated authorization-scoped search

**Dependencies:** Task 6.1

**Files:**

- Create: `src/main/java/com/pablomarotta/smart_task_manager/dto/TaskSearchCriteria.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/dto/TaskSearchResult.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/service/TaskQueryService.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/repository/TaskRepository.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/controller/TaskController.java`
- Add indexes in a new migration only if query-plan fixtures justify them.
- Create matching unit, repository, MockMvc, security, and integration tests.

**Steps:**

1. Add failing tests for query/project/status/priority/assignee/label/due filters and combinations.
2. Implement projection-based pageable queries restricted to accessible projects.
3. Enforce size 1-50, free-text minimum, validated sort allowlist, and ID tie-breaker.
4. Prove existing unscoped title queries are never exposed.
5. Capture query plans on representative fixtures before adding indexes.

**Acceptance criteria:** Search never returns inaccessible tasks; filters compose deterministically; no unbounded result endpoint or lazy entity graph backs global search.

### Task 6.3: Add Search and label frontend

**Dependencies:** Tasks 6.1 and 6.2

**Files:**

- Modify: `frontend/api.js`
- Modify: `frontend/api.test.js`
- Create: `frontend/components/SearchSection.jsx`
- Create: `frontend/components/SearchFilters.jsx`
- Create: `frontend/components/ProjectLabelCatalog.jsx`
- Create: `frontend/components/TaskLabelPicker.jsx`
- Create matching tests.
- Modify Board/My Work/Projects ticket displays.
- Modify: `frontend/App.jsx`
- Modify: `frontend/App.test.jsx`
- Modify: `frontend/styles.css`

**Steps:**

1. Add failing tests for debouncing, stale response suppression, URL/state filters, pagination, and label permissions.
2. Add Search navigation and result cards that open the existing ticket sheet.
3. Add label chips/catalog/pickers across task surfaces.
4. Separate loading, no-results, no-access, and server-error states.
5. Run frontend tests and build.

**Acceptance criteria:** Filters survive navigation as designed, stale requests cannot replace newer results, and label presentation is consistent across every ticket surface.

---

## Wave 7: Templates and shared materialization

### Task 7.1: Extract deterministic project blueprint materialization

**Dependencies:** Wave 6

**Files:**

- Create: `src/main/java/com/pablomarotta/smart_task_manager/dto/blueprint/ProjectBlueprint.java`
- Create supporting blueprint DTOs.
- Create: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectBlueprintValidator.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectBlueprintMaterializer.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectGenerationConfirmationService.java`
- Modify matching tests and planning fixtures.

**Steps:**

1. Characterize existing AI confirmation with failing extraction tests.
2. Extract graph validation, label-ready mapping, and transactional project/task persistence.
3. Adapt `ProjectPlanDraft` into a generic blueprint without changing the AI HTTP contract.
4. Re-run all AI planning confirmation and integration tests.

**Acceptance criteria:** Existing AI-confirmed projects are byte-for-byte equivalent at the API contract level; graph validation has one deterministic implementation reusable by templates.

### Task 7.2: Implement versioned project templates

**Dependencies:** Task 7.1

**Files:**

- Create: `src/main/resources/db/migration/V14__add_project_templates.sql`
- Create template and instantiation models/repositories/DTOs/services/controllers.
- Create matching unit, MockMvc, security, idempotency, and integration tests.

**Steps:**

1. Add failing blueprint sanitization, ownership, optimistic-version, and idempotency tests.
2. Save immutable versioned blueprints without runtime state or AI identifiers.
3. Implement owned template CRUD and preview.
4. Instantiate through `ProjectBlueprintMaterializer` using a client UUID.

**Acceptance criteria:** Templates preserve labels, order, criteria, dependencies, and estimates; repeated client IDs return the original project; templates never copy assignees, dates, attachments, runtime status, or planning metadata.

### Task 7.3: Add template gallery and save-as-template UI

**Dependencies:** Task 7.2

**Files:**

- Modify: `frontend/api.js`
- Modify: `frontend/api.test.js`
- Create: `frontend/components/TemplateGallery.jsx`
- Create: `frontend/components/TemplatePreview.jsx`
- Create: `frontend/components/SaveProjectTemplateDialog.jsx`
- Create matching tests.
- Modify: `frontend/components/ProjectCreatePanel.jsx`
- Modify: `frontend/components/ProjectDesk.jsx`
- Modify: `frontend/App.test.jsx`
- Modify: `frontend/styles.css`

**Steps:**

1. Add failing gallery, preview, save, instantiate, retry, and error tests.
2. Place templates beside Blank project and Plan with AI.
3. Display exact ticket/label preview and protect duplicate submissions with one client UUID.
4. Run frontend tests and build.

**Acceptance criteria:** Users can save and instantiate templates with honest previews; network retries do not duplicate projects.

---

## Wave 8: Recurring tickets and attachments

### Task 8.1: Implement idempotent recurring tickets

**Dependencies:** Wave 7 and activity from Wave 4

**Files:**

- Create: `src/main/resources/db/migration/V15__add_recurring_tickets.sql`
- Create recurrence rule/occurrence models, repositories, DTOs, service, controller, and scheduler.
- Create matching unit, repository, MockMvc, security, concurrency, DST, and integration tests.

**Steps:**

1. Add failing next-occurrence tests across cadence, month ends, timezones, DST, end dates, and fake clock.
2. Add a unique `(rule_id,scheduled_for)` occurrence constraint and concurrent scheduler test.
3. Snapshot permitted ticket fields and create one bounded catch-up occurrence.
4. Clear invalid assignees and forbidden runtime/attachment/dependency state.
5. Record generated-ticket activity.

**Acceptance criteria:** Multiple pollers create one occurrence; restarts and retries are idempotent; no unbounded backlog is created; generated ticket data follows the design exactly.

### Task 8.2: Implement authorized attachment storage

**Dependencies:** Wave 7 and activity from Wave 4; may run parallel with Task 8.1 after V15 is assigned

**Files:**

- Create: `src/main/resources/db/migration/V16__add_task_attachments.sql`
- Create attachment model/repository/DTOs/service/controller.
- Create: `src/main/java/com/pablomarotta/smart_task_manager/service/AttachmentStorage.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/service/LocalAttachmentStorage.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `frontend/api.js` request helper for `FormData` and `Blob`.
- Create matching unit, MockMvc, security, failure-path, and integration tests.

**Steps:**

1. Add failing traversal, size, quota, type/signature, cross-project, and failed-transaction cleanup tests.
2. Store generated keys and metadata only in PostgreSQL.
3. Stage file I/O outside database transactions, finalize metadata safely, and clean failures.
4. Force attachment download disposition and re-authorize each operation.
5. Queue storage cleanup before aggregate deletion.

**Acceptance criteria:** Filenames cannot influence paths; unauthorized UUID knowledge grants nothing; invalid uploads leave no file or metadata; tested delete/failure paths leave no orphan.

### Task 8.3: Add recurrence and attachment UI

**Dependencies:** Tasks 8.1 and 8.2

**Files:**

- Modify: `frontend/api.js`
- Modify: `frontend/api.test.js`
- Create: `frontend/components/RecurrenceEditor.jsx`
- Create: `frontend/components/TaskAttachments.jsx`
- Create matching tests.
- Modify: `frontend/components/TicketDetailPanel.jsx`
- Modify: `frontend/App.test.jsx`
- Modify: `frontend/styles.css`

**Steps:**

1. Add failing preview, create/edit/disable, upload/progress/download/delete, quota, and rollback tests.
2. Add recurrence management for managers and read-only schedule detail for members.
3. Add attachment list/upload/download/delete with clear validation failures.
4. Run frontend tests and build.

**Acceptance criteria:** Role-aware recurrence and attachment actions work without full-page refreshes; upload errors preserve the rest of ticket state; keyboard and screen-reader labels cover every control.

---

## Wave 9: Analytics and reports

### Task 9.1: Implement authorized reports and safe CSV

**Dependencies:** Waves 4, 6, and 8

**Files:**

- Create report projection DTOs, repository queries, service, and controller.
- Reuse `TaskSearchCriteria` for task export.
- Create matching unit, repository, MockMvc, security, accuracy, and integration tests.

**Steps:**

1. Add failing fixture-based totals for workspace and project scope.
2. Implement bounded date ranges and SQL aggregate projections.
3. Separate current-state metrics from activity-derived historical metrics.
4. Add CSV using identical authorized filters and neutralize `=`, `+`, `-`, and `@` cells.
5. Measure representative query plans and add a sequential migration for evidence-backed indexes only.

**Acceptance criteria:** Fixture totals exactly match accessible rows; inaccessible project data never contributes; the API does not invent unavailable history; CSV cannot trigger spreadsheet formulas.

### Task 9.2: Add Reports frontend

**Dependencies:** Task 9.1

**Files:**

- Modify: `frontend/api.js`
- Modify: `frontend/api.test.js`
- Create: `frontend/components/ReportsSection.jsx`
- Create: `frontend/components/ProjectReport.jsx`
- Create matching tests.
- Modify: `frontend/components/ProjectDesk.jsx`
- Modify: `frontend/App.jsx`
- Modify: `frontend/App.test.jsx`
- Modify: `frontend/styles.css`

**Steps:**

1. Add failing summary, date-range, empty/error, accessible-bar, and CSV tests.
2. Add Reports navigation and project report entry.
3. Render current snapshots separately from historical measures.
4. Use semantic HTML/CSS bars before adding any chart dependency.
5. Run frontend tests and build.

**Acceptance criteria:** Reports render accurate values and accessible descriptions; date filters and exports use the same authorization-scoped server queries.

---

## Wave 10: AI consolidation and system verification

### Task 10.1: Remove the duplicate Spring model-provider path

**Dependencies:** Existing planning regression suite remains green

**Files:**

- Delete or delegate: `src/main/java/com/pablomarotta/smart_task_manager/controller/AIController.java`
- Delete or delegate: `src/main/java/com/pablomarotta/smart_task_manager/service/AIService.java`
- Delete or delegate: `src/main/java/com/pablomarotta/smart_task_manager/service/OllamaService.java`
- Delete obsolete AI classification/Ollama DTOs and tests.
- Modify: `pom.xml`
- Modify: `README.md`
- Modify API/security regression tests.

**Steps:**

1. Add an architecture test proving no Spring production class invokes a model provider directly.
2. Confirm no supported frontend workflow depends on the legacy endpoint.
3. Remove the route, blocking WebFlux dependency, obsolete client dependency, DTOs, and tests.
4. Run all existing FastAPI planning, Spring planning, and frontend planning tests.

**Acceptance criteria:** FastAPI is the only model-provider boundary; existing explicit planning flows remain unchanged; no general autonomous graph or model-selected tool path is added.

### Task 10.2: Add critical end-to-end journeys

**Dependencies:** Waves 0-10.1

**Files:**

- Modify: `package.json`
- Create Playwright configuration and tests under `e2e/`.
- Create deterministic mocked FastAPI provider fixtures.
- Modify: `.github/workflows/ci.yml`
- Modify: `docker-compose.yml`

**Steps:**

1. Add a disposable full-stack harness with PostgreSQL, Mailpit, Spring, Vite, and mocked AI.
2. Implement the critical journey: register, verify, invite, accept, role-aware ticket creation/assignment, comment/mention, notification read, search/filter, activity, attachment, recurrence, template, and report.
3. Add negative journeys for nonmember enumeration, expired tokens, forbidden role escalation, and failed upload cleanup.
4. Capture traces/screenshots only on failure and ensure artifacts contain no secrets.

**Acceptance criteria:** Critical and negative browser journeys pass from a clean checkout without live Ollama or external email/storage services.

### Task 10.3: Run review, security audit, and issue remediation

**Dependencies:** Task 10.2

**Files:** All changed files, tests, migrations, documentation, and CI configuration.

**Steps:**

1. Run Java/Spring code review, database/JPA review, frontend review, Python review, security audit, and engineering-guidelines review using agents that did not own the implementation.
2. Classify every finding as critical, important, minor, or rejected-with-evidence.
3. Obtain user approval before applying reviewer-requested changes when required by the team-building workflow.
4. Assign non-overlapping remediation ownership and add a regression test for every accepted correctness/security issue.
5. Re-run focused tests after each fix and then the complete verification gate.

**Acceptance criteria:** No unresolved critical or important finding remains; rejected findings have concrete evidence; every accepted issue has a witnessed failing-then-passing regression test.

### Task 10.4: Complete the requirement-by-requirement audit

**Dependencies:** Task 10.3

**Verification commands:**

- `mvn test`
- tagged Testcontainers integration command established in Task 0.1
- `npm test`
- `npm run build`
- `npm run test:e2e`
- `cd ai-service && uv run pytest`
- `cd ai-service && uv run ruff check .`
- `cd ai-service && uv run pyright`
- `git diff --check`
- clean-worktree and migration-order checks

**Steps:**

1. Map every design completion criterion and every task acceptance criterion to current code, test output, or browser evidence.
2. Treat missing or indirect evidence as incomplete and continue remediation.
3. Verify local adapter documentation and clean-checkout startup instructions.
4. Only after the complete matrix is proven, prepare the final merge/PR handoff.

**Acceptance criteria:** Every explicit feature and invariant in the design has authoritative evidence; all verification commands pass in the intended environment; no uncommitted or untracked implementation artifact remains.

---

## Team execution rules

- The lead owns integration, migration numbering, shared authorization contracts, `frontend/App.jsx`, `frontend/styles.css`, and final commits.
- Implementation agents receive explicit file ownership and must not revert or overwrite work from other agents.
- No dependent wave starts before its prerequisite wave is integrated and green.
- Backend and frontend work within a stable wave may run in parallel only when file ownership does not overlap.
- Tests are written and witnessed failing before implementation code for each behavior.
- Reviewers and security auditors do not implement their own findings.
- Reviewer-requested remediation is applied only after the required approval gate.
- The local `main` branch is never modified directly; all work remains on `feature/product-capabilities-epic` until the final verified merge workflow.
