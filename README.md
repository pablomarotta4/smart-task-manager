# Smart Task Manager

Smart Task Manager is a Java 21/Spring Boot API backed by PostgreSQL. Its AI project-planning
flow turns a short brief into an editable project draft and first backlog, evaluates whether that
backlog is sufficient and non-repetitive, and creates the project and tickets only after explicit
human confirmation.

## Architecture

```text
client
  -> Spring Boot API (auth, workflow, confirmation, database writes)
       -> FastAPI AI service (LangGraph, prompts, quality review)
            -> Ollama (replaceable model provider)
  -> PostgreSQL (users, drafts, projects, tickets, criteria, dependencies)
```

The AI service cannot access the business database. Spring derives ownership from the JWT-authenticated
principal, stores the draft, and performs confirmation in one transaction. A repeated confirmation is
idempotent and returns the existing project.

The LangGraph workflow is bounded:

```text
analyze brief -> generate -> assess -> (passed) finalize
                              (failed) revise once -> assess -> finalize
```

Brief analysis extracts an explicit capability checklist. Assessment combines deterministic structure,
repetition, actionability, and checklist-coverage checks. If the second output is still weak, the API
returns it with `quality.passed=false`; valid JSON alone is not treated as a good plan.

## Run locally

Requirements: Java 21, Maven, Docker, Python 3.12+, and `uv`.

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Run Ollama and the AI service either through Compose:

```bash
docker compose --profile ai up -d ollama ai-service
docker compose --profile ai exec ollama ollama pull llama3.2:3b
```

Or run an existing host Ollama plus FastAPI directly:

```bash
cd ai-service
uv sync
uv run uvicorn smart_task_ai.main:app --port 8000
```

Then run Spring with Java 21:

```bash
mvn spring-boot:run
```

Useful configuration:

- `AI_PLANNING_BASE_URL` defaults to `http://127.0.0.1:8000`.
- `AI_PLANNING_CONNECT_TIMEOUT_MS` defaults to `2000`.
- `AI_PLANNING_READ_TIMEOUT_MS` defaults to `180000`.
- `SMART_TASK_AI_OLLAMA_BASE_URL` defaults to `http://127.0.0.1:11434`.
- `SMART_TASK_AI_OLLAMA_MODEL` defaults to `llama3.2:3b`.

## Generate a project

First register or log in through `/api/auth/**` and use the returned bearer token.

Create a draft (this does not create a project or tickets):

```http
POST /api/project-generation-runs
Authorization: Bearer <token>
Content-Type: application/json

{
  "prompt": "Build a personal meal planner that creates a weekly plan and shopping list"
}
```

The response contains `runId`, an editable `draft`, the quality score and issue codes, revision count,
and model name. Present the full draft to the user and let them edit it.

Confirm the edited draft:

```http
POST /api/project-generation-runs/{runId}/confirm
Authorization: Bearer <token>
Content-Type: application/json

{
  "draft": {
    "name": "Meal Planner MVP",
    "objective": "...",
    "assumptions": [],
    "risks": [],
    "tickets": ["the complete edited ticket objects from the draft response"]
  }
}
```

Confirmation creates the project, TODO tickets, estimates, due dates, acceptance criteria, and dependency
links atomically. Calling the same confirmation again returns HTTP 200 and `alreadyConfirmed=true`.

## Test and evaluate behavior

```bash
# Python graph, provider, API, and deterministic behavior tests
cd ai-service
uv run pytest
uv run ruff check .
uv run pyright

# Optional live behavior evaluation against the configured Ollama model
uv run python scripts/evaluate_planner.py

# Java unit and PostgreSQL integration tests
cd ..
mvn test
```

The behavior gate reports ticket count, unique normalized-title ratio, maximum title similarity,
description coverage, acceptance-criteria coverage, required-concept coverage, score, issue codes, and
whether revision was needed.
