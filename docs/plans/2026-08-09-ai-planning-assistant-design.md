# AI Planning Assistant Design

- Status: Accepted
- Date: 2026-08-09
- Scope: Smart Task Manager backend and a new internal AI planning service

## Summary

Replace the current one-shot task-classification prompt with a bounded planning
assistant. A user explicitly selects an existing task, requests an AI plan,
answers clarification questions only when necessary, edits the proposed plan,
and confirms it before any subtasks are created.

The existing Spring Boot application remains the public API and system of
record. A new Python service, built with FastAPI and LangGraph, owns prompt and
model orchestration. Both applications live in the same repository but run as
separate processes. The AI service never writes to the task-manager tables.

## Context

The current implementation calls Ollama from `TaskService.createTask` and asks
one prompt to classify priority and category, estimate completion time, and
produce a summary. This is sufficient for basic enrichment but becomes hard to
extend when planning requires:

- focused prompts with separate responsibilities;
- conditional clarification;
- task decomposition and dependency analysis;
- review and bounded revision loops;
- durable pause and resume points;
- editable human approval before writes;
- provider-independent model selection.

Implementing those concerns directly inside the Spring transaction would
couple task persistence to model orchestration and make the graph difficult to
evolve and test.

## Goals

- Start planning only through an explicit `Plan with AI` action.
- Plan a selected task using that task and other tasks in the same project.
- Ask targeted clarification questions only when essential context is missing.
- Produce an editable, structured plan containing:
  - a refined objective;
  - assumptions and risks;
  - ordered subtasks;
  - acceptance criteria;
  - estimates, priorities, dependencies, and suggested deadlines.
- Require explicit confirmation before creating subtasks.
- Keep Spring Boot responsible for authentication, authorization, validation,
  idempotency, and all business writes.
- Start with Ollama while keeping the graph independent of the model provider.
- Persist graph state so interrupted runs survive service restarts.
- Keep CI deterministic and independent of a live model.

## Non-goals

- Automatically planning every new task.
- Allowing the AI service to modify task-manager data directly.
- Building an open-ended project copilot, autonomous agent, or multi-agent
  system.
- Cross-project or long-term user memory in the first version.
- Introducing a message broker or independent worker fleet.
- Treating model output as trusted business data.
- Supporting unbounded clarification, review, or retry loops.

## Considered Approaches

### 1. Keep orchestration in Spring Boot

This has the smallest deployment footprint but couples task persistence to AI
control flow. Durable graph state, interruptions, and prompt experimentation
would be awkward and would continue the current remote-call-inside-transaction
problem.

### 2. Two services in one monorepo

This is the selected approach. Spring Boot owns business behavior while a
Python service owns the graph. The services share versioned contracts and a
local Docker Compose environment but can be tested and deployed independently.

### 3. Fully asynchronous AI platform

A broker, worker fleet, callbacks, and independent release lifecycle would
support longer or higher-volume workloads. This is unnecessary for the first
explicit, user-triggered planning workflow and can be added later if measured
latency or scale requires it.

## Repository Structure

Keep the existing Java application at the repository root to avoid a broad
source move. Add the AI application alongside it:

```text
smart-task-manager/
├── src/                         Spring Boot application
├── ai-service/                  Python, FastAPI, and LangGraph
│   ├── src/
│   │   └── smart_task_ai/
│   │       ├── api/             Internal HTTP routes
│   │       ├── contracts/       Pydantic request/response models
│   │       ├── graph/           State, nodes, edges, and routing
│   │       ├── models/          Provider-independent model interface
│   │       ├── providers/       Ollama first; other providers later
│   │       ├── prompts/         Versioned focused prompts
│   │       └── observability/   Correlation and metrics helpers
│   ├── tests/
│   └── pyproject.toml
├── contracts/                   Versioned cross-service schemas and fixtures
├── docker-compose.yml
└── docs/
```

## Ownership Boundaries

### Spring Boot owns

- browser-facing APIs;
- JWT authentication and task/project authorization;
- the selected task and project-context snapshot;
- planning-run business metadata;
- context freshness checks;
- edited-plan validation at the write boundary;
- transactional and idempotent subtask creation;
- task, dependency, and acceptance-criteria persistence.

### The AI service owns

- the LangGraph definition and graph state;
- focused prompt templates and their versions;
- provider selection and Ollama integration;
- structured model-output parsing and repair;
- clarification and review interruptions;
- graph checkpoints and run-level AI metadata;
- AI-specific latency, error, and quality signals.

The AI service has no credentials for the task-manager schema. Spring sends a
sanitized snapshot; the AI service does not fetch business data itself.

## High-level Data Flow

```text
User selects Plan with AI
          |
          v
Spring verifies task ownership and loads project context
          |
          v
Spring creates planning_run and calls internal FastAPI
          |
          v
LangGraph executes until clarification or draft review
          |
          +--> clarification required --> user answers --> resume
          |
          v
User edits complete draft and confirms
          |
          v
Spring checks context freshness and resumes review checkpoint
          |
          v
AI service validates edited draft and returns CONFIRMED
          |
          v
Spring creates all subtasks and relationships in one transaction
```

## Planning Graph

The graph is a controlled workflow rather than an open-ended autonomous agent:

```text
START
  |
  v
validate_context
  |
  v
analyze_task
  |
  +-- missing essential context --> ask_clarification
  |                                  |
  |                                  +-- human answer --> analyze_task
  |
  v
generate_subtasks
  |
  v
enrich_plan
  |
  v
review_plan
  |
  +-- issues --> revise_plan --> review_plan
  |
  v
human_review
  |
  +-- reject --> CANCELLED
  |
  +-- approve edited plan --> CONFIRMED
```

### Node responsibilities

- `validate_context` performs deterministic schema, size, and identifier checks.
- `analyze_task` identifies the objective, constraints, assumptions, risks, and
  missing information.
- `ask_clarification` interrupts with targeted questions and resumes with the
  user's answers.
- `generate_subtasks` creates ordered, concrete work items and acceptance
  criteria.
- `enrich_plan` assigns priorities, estimates, dependencies, and suggested due
  dates using the project snapshot.
- `review_plan` checks completeness, duplication, unrealistic estimates,
  circular dependencies, and internal consistency.
- `revise_plan` addresses review findings.
- `human_review` interrupts with the complete editable draft and resumes with
  an approve or reject decision.

Clarification is limited to two rounds. Automatic review revision is limited to
one round. Model and network retries are bounded separately. Every loop has an
explicit terminal path.

### Graph state

State contains only data needed for the current run:

- `run_id` and `contract_version`;
- immutable selected-task snapshot;
- immutable same-project task snapshot;
- task timestamp and project-context hash;
- clarification round, questions, and answers;
- analysis result;
- generated and revised draft versions;
- review findings and revision count;
- provider, model, and prompt-version metadata;
- current status and sanitized error information.

Raw state is stored and resumed under `thread_id = run_id`. Nodes return partial
state updates instead of mutating shared state. The first version does not use a
cross-thread long-term-memory store.

## Model and Prompt Design

The graph depends on a provider-independent model interface. Ollama is the
first adapter and remains an external process. Adding another provider must not
change graph nodes or contracts.

Prompts are separated by responsibility and versioned independently:

- task analysis and clarification;
- subtask generation;
- plan enrichment;
- plan review;
- plan revision;
- structured-output repair.

Each model call returns a strict Pydantic model with unknown fields forbidden.
Free-form model text never becomes a cross-service or persistence contract.
Invalid output receives one schema-aware repair attempt, after which the run
fails explicitly.

## Public Spring API

```text
POST /api/tasks/{taskId}/planning-runs
GET  /api/planning-runs/{runId}
POST /api/planning-runs/{runId}/clarifications
POST /api/planning-runs/{runId}/confirm
POST /api/planning-runs/{runId}/cancel
```

### Start a run

`POST /api/tasks/{taskId}/planning-runs` authenticates the user, authorizes the
task and project, creates the business run record, captures a context version,
and invokes the AI service. It returns the current state: clarification needed,
draft ready, or failed.

### Retrieve a run

`GET /api/planning-runs/{runId}` authorizes against the Spring planning-run
record. Spring returns its business status and, for an active run, retrieves the
current graph-facing payload from the AI service. This supports browser refresh
while a run is waiting for input.

### Submit clarification

`POST /api/planning-runs/{runId}/clarifications` accepts answers for the current
interrupt only. It validates the run owner and status, then resumes the same
graph thread.

### Confirm a draft

`POST /api/planning-runs/{runId}/confirm` accepts the entire user-edited plan,
not a patch. Spring checks authorization, contract version, draft version,
context freshness, field limits, dependency references, and dates before
resuming the graph review checkpoint.

After the AI service returns `CONFIRMED`, Spring persists the complete plan in
one transaction. Repeating confirmation for an applied run returns the existing
created tasks.

### Cancel a run

`POST /api/planning-runs/{runId}/cancel` marks a non-terminal run cancelled and
never deletes or changes the selected task.

## Internal AI API

```text
POST /internal/v1/planning-runs
GET  /internal/v1/planning-runs/{runId}
POST /internal/v1/planning-runs/{runId}/resume
```

The resume endpoint accepts a discriminated request payload:

- `clarification_answers`, containing the answers for a pending clarification;
- `review_decision`, containing rejection or the complete edited plan.

Spring authenticates with a dedicated internal credential. User JWTs are not
forwarded. The service must not accept direct browser traffic.

## Structured Plan Contract

Every cross-service request and response includes `contractVersion: "v1"`.
The checked-in schema is generated from the Python Pydantic models and verified
against Java fixtures.

```text
PlanningDraft
  contractVersion
  runId
  draftVersion
  objective
  assumptions[]
  risks[]
  subtasks[]
    clientId
    title
    description
    acceptanceCriteria[]
    priority
    estimatedHours
    suggestedDueDate
    dependsOnClientIds[]
```

`clientId` is unique inside the draft and allows dependencies to be expressed
before database task IDs exist. Spring resolves these references during the
transaction. Circular, missing, and self-dependencies are rejected.

## Run Lifecycle

```text
RUNNING
  -> NEEDS_CLARIFICATION -> RUNNING
  -> DRAFT_READY
  -> CONFIRMED
  -> APPLIED
```

Terminal alternatives are `CANCELLED` and `FAILED`. The AI graph ends at
`CONFIRMED`, `CANCELLED`, or `FAILED`. `APPLIED` is a Spring-only business status
indicating that the confirmed plan was persisted.

If the graph reaches `CONFIRMED` but the Spring transaction fails, the Spring
run remains confirmed but unapplied. A repeated confirmation can retrieve the
confirmed plan and retry the idempotent application without rerunning the
model.

## Spring Persistence Changes

### `planning_runs`

Add a table containing at minimum:

- UUID primary key;
- selected task ID;
- requesting user ID;
- status;
- contract and draft versions;
- selected task `updated_at` captured at start;
- project-context hash;
- applied timestamp;
- sanitized error code;
- created and updated timestamps.

### Task hierarchy and lineage

Add nullable fields to `tasks`:

- `parent_task_id`, referencing the selected parent task;
- `estimated_hours`;
- `planning_run_id`;
- `planning_client_id`.

The pair `(planning_run_id, planning_client_id)` is unique and is the database
idempotency boundary for generated subtasks. Deleting a parent should not
silently delete completed work; `parent_task_id` should use `ON DELETE SET NULL`.

### Acceptance criteria

Add `task_acceptance_criteria` with task ID, stable order, text, and completion
state. Criteria are separate rows so they can later be checked independently.

### Dependencies

Add `task_dependencies(task_id, depends_on_task_id)` with a composite primary
key and constraints preventing self-dependency. Spring validates acyclicity
before insertion.

## Persistence and Idempotency

The AI service uses a PostgreSQL-backed LangGraph checkpointer. It may use the
same PostgreSQL server during local development but has a separate schema and
separate credentials from the Spring application. In-memory checkpointing is
test-only.

No non-idempotent business side effects occur inside graph nodes. Interrupt
nodes are safe to re-execute when resumed. Spring writes only after the graph
returns a confirmed plan.

Confirmation is idempotent at two levels:

- the `planning_runs` state prevents applying a run twice;
- the unique planning-run/client-ID pair prevents duplicate generated tasks if
  a retry crosses an uncertain transaction boundary.

## Context Freshness

Spring captures the selected task's `updated_at` and a deterministic hash of
the same-project context sent to the graph. Immediately before confirmation it
recomputes both.

If either changed, confirmation returns `409 PLAN_CONTEXT_STALE`. The stale plan
is not applied. The user starts a new planning run so dependencies and deadlines
are based on current data.

## Error Handling

- Invalid user input or edited drafts return structured validation errors.
- Missing resources and authorization failures are handled by Spring before an
  internal AI call.
- Transient provider failures receive bounded retries.
- Invalid structured model output receives one repair attempt.
- Persistent model, parsing, or checkpoint failures mark the run `FAILED`.
- A failed plan never creates partial subtasks.
- Any failure inside the Spring application transaction rolls back all tasks,
  dependencies, and acceptance criteria.
- Error responses use stable machine-readable codes and safe messages. Raw
  provider responses and internal exceptions are not returned to browsers.

The current silent empty-classification fallback is removed for the planning
flow. Planning failure remains non-destructive but visible.

## Security

- Spring performs authentication and resource authorization for every public
  planning endpoint.
- The AI service is internal-only and accepts Spring's service identity.
- Browser JWTs and user credentials never enter graph state.
- Request sizes, task counts, string lengths, clarification rounds, subtasks,
  dependencies, and model calls are bounded.
- Task and project text is treated as untrusted prompt input and cannot override
  system instructions or grant tools.
- The first graph has no write tools and no arbitrary network or filesystem
  tools.
- Secrets come from environment or a secret manager and are never included in
  contracts, checkpoints, or logs.
- Separate database credentials enforce the data-ownership boundary.

## Observability

Propagate `run_id` as the correlation identifier through Spring, FastAPI,
LangGraph, and the model provider.

Structured boundary logs include:

- event name;
- run, task, and project IDs;
- graph node;
- provider and model names;
- prompt version;
- duration and outcome;
- retry count and sanitized error category.

Logs exclude raw prompts, task descriptions, clarification answers, complete
model responses, credentials, and browser tokens.

Metrics cover:

- end-to-end and per-node latency;
- planning failure rate;
- clarification frequency and rounds;
- repair and revision frequency;
- provider/model usage;
- draft approval, rejection, and edit rates;
- confirmation and application failures.

Distributed traces connect Spring, FastAPI, and Ollama calls using the same run
identifier.

## Testing Strategy

CI must not require Ollama or a cloud model.

### AI service tests

Use a scripted fake model to prove:

- sufficient context bypasses clarification;
- missing context interrupts and resumes with the same thread ID;
- clarification stops after two rounds;
- invalid output receives one repair attempt;
- review findings cause at most one revision;
- edited approval replaces the generated draft;
- rejection and failures reach the correct terminal state;
- a persisted checkpoint resumes after a simulated restart.

Pydantic tests cover limits, unknown-field rejection, contract versioning,
dependency references, dates, and discriminated resume payloads. FastAPI tests
cover internal authentication, response filtering, validation failures, and
correlation propagation.

### Spring tests

Test:

- task/project ownership on every public endpoint;
- project-context snapshot generation;
- lifecycle transitions;
- stale-context rejection;
- edited-plan validation;
- cycle and missing-dependency rejection;
- transactional creation and rollback;
- repeated confirmation and idempotency;
- safe mapping of internal service failures.

### Contract tests

Check in valid and invalid `v1` examples. Python generates the JSON Schema;
Java must deserialize valid fixtures and reject incompatible ones. CI fails on
unreviewed schema drift.

### End-to-end tests

Run Spring, FastAPI, PostgreSQL, and a deterministic Ollama-compatible stub in
Docker Compose. A separate optional smoke test may exercise a real local Ollama
model, but its wording and quality are not CI assertions.

### Prompt-quality evaluation

Maintain a small versioned dataset of representative planning tasks. Evaluate
required fields, dependency validity, reasonable decomposition, and rubric
scores rather than exact text. Prompt evaluation reports regressions separately
from deterministic correctness tests.

## Rollout

1. Add contracts, persistence migrations, and the AI-service skeleton behind a
   disabled feature flag.
2. Implement and test the graph using a fake provider.
3. Integrate Ollama and the Spring internal client.
4. Expose public planning endpoints with the UI action still hidden.
5. Enable the action for development users.
6. Measure latency, clarification rate, failures, and plan acceptance.
7. Enable it broadly only after authorization, idempotency, transaction, and
   restart-resume tests pass.

## Definition of Done

- A user can explicitly start a run for a task they are authorized to access.
- The graph receives only that task and current same-project context.
- The graph can pause for targeted clarification and resume after restart.
- It produces a strict full planning draft.
- The user can edit the complete draft before confirmation.
- No task is created before confirmation.
- Spring rejects stale, invalid, unauthorized, or duplicate confirmation.
- Confirmed subtasks, criteria, and dependencies are created atomically.
- The graph is provider-independent and works with Ollama through an adapter.
- Deterministic tests cover every branch without a live model.
- Logs and checkpoints contain no credentials or browser tokens.

## Future Extensions

Only consider these after evidence from the first version:

- additional model providers;
- asynchronous queue/worker execution for long-running plans;
- streaming progress events;
- long-term planning preferences;
- cross-project portfolio planning;
- deeper project tools or retrieval;
- independent deployment and scaling of the AI service.
