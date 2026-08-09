# AI Project Generation Implementation Plan

> **For Codex:** Execute this plan task by task with test-driven development and verification gates.

**Goal:** Let an authenticated user submit a short project brief, receive a useful and non-repetitive AI-generated project draft, edit it, and confirm it to atomically create the project and its first tickets.

**Architecture:** Keep the Spring Boot application as the public API, authentication boundary, workflow store, and system of record. Add a Python FastAPI service that owns provider-independent model access and a LangGraph workflow with generation, deterministic quality assessment, one bounded revision, and finalization. Spring stores the returned draft and requires explicit confirmation before writing projects, tasks, acceptance criteria, or dependencies.

**Tech Stack:** Java 21, Spring Boot 3.4.1, Spring Data JPA, PostgreSQL/Flyway, Python 3.12+, FastAPI, Pydantic v2, LangGraph, httpx, Ollama, pytest, JUnit 5, Mockito.

## Files to Understand

- `docs/plans/2026-08-09-ai-planning-assistant-design.md` — accepted service and graph boundaries.
- `src/main/java/com/pablomarotta/smart_task_manager/model/Project.java` — current project persistence model.
- `src/main/java/com/pablomarotta/smart_task_manager/model/Task.java` — current task persistence model.
- `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectService.java` — existing project creation conventions.
- `src/main/java/com/pablomarotta/smart_task_manager/config/SecurityConfig.java` — authenticated API boundary.
- `src/main/resources/db/migration/V1__init.sql` — current PostgreSQL schema.
- `docker-compose.yml` — local PostgreSQL runtime.

## Contract and Invariants

- A caller supplies only a project prompt. The owner identity always comes from Spring Security.
- The AI service receives no credentials and has no access to the business database.
- A draft contains a project name, objective, assumptions, risks, and 3–12 tickets.
- Every ticket has a stable client ID, unique actionable title, description, priority, estimate, acceptance criteria, and optional dependencies on other client IDs.
- Deterministic validation rejects duplicate client IDs, unknown/self dependencies, invalid priority values, empty content, and cyclic dependencies.
- Quality assessment flags thin drafts, duplicate or highly similar titles, missing descriptions or acceptance criteria, and invalid dependencies.
- The graph may revise at most once. It always returns the final quality report so low-quality output is visible rather than silently accepted.
- Draft generation never writes a project or task.
- Confirmation is authenticated, owner-scoped, transactional, and idempotent.
- Model/network calls never run inside a database transaction.

### Task 1: Scaffold the AI service and strict contracts

**Files:**

- Create: `ai-service/pyproject.toml`
- Create: `ai-service/.python-version`
- Create: `ai-service/src/smart_task_ai/__init__.py`
- Create: `ai-service/src/smart_task_ai/contracts.py`
- Create: `ai-service/tests/test_contracts.py`

**Steps:**

1. Write failing Pydantic contract tests for valid drafts and invalid counts, duplicate IDs, dependencies, priorities, and text limits.
2. Run `uv run pytest tests/test_contracts.py` and confirm the import/test failure.
3. Implement strict Pydantic v2 request, draft, ticket, dependency, quality, and response models with `extra="forbid"`.
4. Re-run the focused test to green.
5. Run Ruff over the new package.

### Task 2: Implement deterministic plan quality evaluation

**Files:**

- Create: `ai-service/src/smart_task_ai/quality.py`
- Create: `ai-service/tests/test_quality.py`

**Steps:**

1. Write failing tests for adequate plans, repeated normalized titles, excessive title similarity, missing useful descriptions, missing acceptance criteria, and dependency cycles.
2. Implement a deterministic evaluator that emits issue codes, metrics, and a 0–100 score.
3. Require a configurable passing score and keep issue text suitable for revision feedback.
4. Re-run the focused tests to green.

### Task 3: Implement the provider-independent LangGraph workflow

**Files:**

- Create: `ai-service/src/smart_task_ai/providers.py`
- Create: `ai-service/src/smart_task_ai/prompts.py`
- Create: `ai-service/src/smart_task_ai/planner.py`
- Create: `ai-service/tests/test_planner.py`

**Steps:**

1. Write failing async tests with a scripted fake model:
   - adequate first output finishes after one model call;
   - repetitive output triggers one revision and returns the improved draft;
   - persistently weak output stops after one revision and exposes the failing report;
   - separate prompts do not leak prior run state.
2. Define an async `PlanningModel` protocol and typed LangGraph state.
3. Build explicit `generate`, `assess`, `revise`, and `finalize` nodes with a conditional edge after assessment.
4. Feed deterministic issue details into the revision prompt and cap revisions at one.
5. Re-run planner tests to green.

### Task 4: Add Ollama and FastAPI adapters

**Files:**

- Create: `ai-service/src/smart_task_ai/settings.py`
- Create: `ai-service/src/smart_task_ai/ollama.py`
- Create: `ai-service/src/smart_task_ai/api.py`
- Create: `ai-service/src/smart_task_ai/main.py`
- Create: `ai-service/tests/test_ollama.py`
- Create: `ai-service/tests/test_api.py`

**Steps:**

1. Write failing tests for Ollama structured JSON parsing, timeout/upstream errors, request validation, and the internal planning response.
2. Implement an httpx-based Ollama adapter using `/api/chat`, JSON-schema structured output, a low generation temperature, and bounded timeouts.
3. Expose `POST /internal/v1/project-plans` and `GET /health` through FastAPI.
4. Return explicit 502/504 responses for provider failures without leaking prompt contents.
5. Re-run all Python tests, Ruff, and type checking.

### Task 5: Add behavior evaluation for sufficiency and repetition

**Files:**

- Create: `ai-service/evals/cases.json`
- Create: `ai-service/scripts/evaluate_planner.py`
- Create: `ai-service/tests/test_behavior_eval.py`
- Create: `ai-service/README.md`

**Steps:**

1. Add representative simple prompts for a personal app, a small business workflow, and a technical service.
2. Write a deterministic evaluation test using scripted model responses and assert:
   - at least 3 tickets per project;
   - unique normalized titles;
   - descriptions and acceptance criteria are sufficiently populated;
   - dependencies reference real tickets and remain acyclic;
   - a deliberately repetitive first output is revised;
   - bounded call counts prevent revision loops.
3. Add an optional live Ollama mode that prints per-case score, issue codes, revision count, and aggregate pass rate.
4. Document which metrics answer “is the response enough?” and “is it repetitive?”.
5. Run the deterministic evaluation and preserve its output for final verification.

### Task 6: Define Spring AI contracts, client, and workflow persistence

**Files:**

- Create: `src/main/java/com/pablomarotta/smart_task_manager/dto/planning/*.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/model/ProjectGenerationRun.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/model/ProjectGenerationStatus.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/repository/ProjectGenerationRunRepository.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/client/AIPlanningClient.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/client/FastApiAIPlanningClient.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/config/AIPlanningProperties.java`
- Create: `src/test/java/com/pablomarotta/smart_task_manager/client/FastApiAIPlanningClientTest.java`
- Modify: `src/main/resources/application.yaml`

**Steps:**

1. Write failing client tests for exact v1 serialization, successful parsing, timeout, and upstream error mapping.
2. Implement immutable Java records for the versioned contract and validate them at the public boundary.
3. Implement a configurable HTTP client with connect/read timeouts and no credentials.
4. Model generation runs with UUID ID, requester, prompt, status, serialized draft, quality metadata, optional applied project, and timestamps.
5. Re-run focused Java tests to green.

### Task 7: Implement authenticated draft generation and atomic confirmation

**Files:**

- Create: `src/main/java/com/pablomarotta/smart_task_manager/service/ProjectGenerationService.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/controller/ProjectGenerationController.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/model/TaskAcceptanceCriterion.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/model/TaskDependency.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/repository/TaskAcceptanceCriterionRepository.java`
- Create: `src/main/java/com/pablomarotta/smart_task_manager/repository/TaskDependencyRepository.java`
- Create: `src/test/java/com/pablomarotta/smart_task_manager/service/ProjectGenerationServiceTest.java`
- Create: `src/test/java/com/pablomarotta/smart_task_manager/controller/ProjectGenerationControllerTest.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/model/Project.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/model/Task.java`

**Steps:**

1. Write failing service tests proving:
   - security username selects the run owner;
   - generation persists a draft but creates no project/tasks;
   - model calls happen before the confirmation transaction;
   - only the run owner can confirm;
   - confirmation creates one project and all tickets atomically;
   - client IDs resolve dependencies correctly;
   - confirmation repeated with the same run returns the same project and creates nothing twice;
   - invalid or failing persistence does not mark the run confirmed.
2. Write failing controller tests for authentication identity, validation, statuses, draft response, confirmation response, and forbidden ownership.
3. Implement generation without `@Transactional` around the AI call and persist explicit `PROCESSING`, `DRAFT_READY`, or `FAILED` states.
4. Implement confirmation in a dedicated transactional method using a pessimistic run lock.
5. Persist acceptance criteria and dependencies only after all generated tasks have IDs.
6. Re-run focused service/controller tests to green.

### Task 8: Migrate the schema and verify PostgreSQL integration

**Files:**

- Create: `src/main/resources/db/migration/V3__add_ai_project_generation.sql`
- Create: `src/test/java/com/pablomarotta/smart_task_manager/integration/ProjectGenerationIntegrationTest.java`
- Modify: `docker-compose.yml`

**Steps:**

1. Add a failing integration test for authenticated generate → inspect draft → edit → confirm → list created tickets → repeat confirm.
2. Add schema changes for project objective, generation runs, task client IDs/estimates/run provenance, acceptance criteria, and dependencies, including foreign keys and unique constraints.
3. Start PostgreSQL and run Flyway from an empty database.
4. Use a stub AI HTTP server in the integration test so the workflow is deterministic and offline.
5. Re-run integration tests and inspect persisted rows.

### Task 9: Local orchestration and operator documentation

**Files:**

- Modify: `docker-compose.yml`
- Modify: `README.md`
- Modify: `.gitignore`

**Steps:**

1. Add the AI service and Ollama configuration to local Compose without baking model downloads into application startup.
2. Document startup, environment variables, API examples, draft editing/confirmation, behavior evaluation, and the optional live-model command.
3. Document that low quality scores remain visible and are not equivalent to successful confirmation.
4. Verify no secrets, local databases, caches, or virtual environments are tracked.

### Task 10: Full verification and commits

**Steps:**

1. Run all Python unit/API/behavior tests from a clean invocation.
2. Run Ruff and type checking from a clean invocation.
3. Run all Java unit and integration tests with Java 21 and PostgreSQL healthy.
4. Run the deterministic cross-service generate/confirm flow.
5. If Ollama is available, run the live behavior evaluation and report model/version/results separately; otherwise document that only the optional live check is unavailable.
6. Review the diff for authz, transaction boundaries, idempotency, prompt leakage, unbounded graph loops, and unrelated changes.
7. Commit logical increments with Conventional Commits and leave the feature branch clean.
