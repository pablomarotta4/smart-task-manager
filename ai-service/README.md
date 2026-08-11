# Smart Task AI

Internal FastAPI and LangGraph service that turns a short project brief into an editable,
quality-scored project draft. It never writes projects or tickets; the Spring API owns human
confirmation and persistence.

## Planning behavior

```text
new project:      analyze brief -> generate -> assess -> optional single revision -> finalize
existing ticket:  derive selected criteria -> generate -> assess -> optional revision -> finalize
```

Even a three-character brief creates a draft immediately. Missing non-critical detail becomes a
visible assumption; up to three `open_questions` identify decisions worth revisiting without blocking
the draft. Analysis extracts the new-project brief's explicit capabilities. Existing-ticket planning
derives them from the selected ticket in code, avoiding an extra LLM call.

Existing project data is compiled under the configured input budget. The selected ticket is kept in
full, up to eight relevant or dependent tickets receive detail, remaining tickets use a compact index
of existing work not to repeat as space permits, and `omitted_task_count` makes truncation explicit.
High-confidence sibling duplicates are omitted deterministically when at least three child tickets
remain, and references to omitted children are cleaned before assessment. Assessment checks
structure, repetition, actionability, capability coverage, selected-ticket drift, and duplication
with existing work. Revision is capped at one model call. A still-weak plan is returned with
`quality.passed=false`; valid JSON alone is not treated as a good plan. An incomplete structured
provider response receives up to two visible, field-specific contract-repair attempts; they do not
consume the quality-revision budget or echo invalid field values into logs.

## Local commands

```bash
uv sync
uv run uvicorn smart_task_ai.main:app --host 0.0.0.0 --port 8000
uv run pytest
uv run ruff check .
uv run pyright
```

Configuration uses the `SMART_TASK_AI_` prefix:

- `SMART_TASK_AI_OLLAMA_BASE_URL` (default `http://127.0.0.1:11434`)
- `SMART_TASK_AI_OLLAMA_MODEL` (default `llama3.2:3b`)
- `SMART_TASK_AI_OLLAMA_TIMEOUT_SECONDS` (default `60`)
- `SMART_TASK_AI_OLLAMA_TEMPERATURE` (default `0.2`)
- `SMART_TASK_AI_OLLAMA_CONTEXT_TOKENS` (default `8192`)
- `SMART_TASK_AI_OLLAMA_OUTPUT_TOKENS` (default `2048`)
- `SMART_TASK_AI_OLLAMA_SEED` (optional)
- `SMART_TASK_AI_PLANNING_INPUT_TOKENS` (default `5000`)

`GET /health` is liveness and never calls the model provider. `GET /ready` calls Ollama's model-list
endpoint and returns `503` when Ollama is unreachable, its response is invalid, or the configured
model is missing.

Every Ollama attempt logs only safe operational fields: model, run ID, graph phase, attempt, prompt
tokens, output tokens, provider duration, and outcome. Prompt and project content are never logged.

## Live verification

Local `gemma3:4b` verification on 2026-08-11 used seed `42`, an `8192`-token context, and a
`2048`-token output budget:

| Scenario | Score | Tickets | Calls | Graph revisions | Prompt / output tokens | Provider time |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Minimal `CRM` brief | 100 | 7 | 2 | 0 | 607 / 984 | 41.3 s |
| Existing bank-import ticket | 100 | 4 | 2 | 0 | 1,623 / 1,185 | 42.8 s |
| Existing ticket in a 200-ticket project | 100 | 5 | 2 | 0 | 11,228 / 1,460 | 45.7 s |

The existing-ticket cases used one structured-contract repair; neither needed the graph's quality
revision.

## Behavior evaluation

The deterministic test suite measures whether responses are enough and whether tickets repeat:

- number of tickets;
- unique normalized-title ratio;
- maximum pairwise title similarity;
- actionable-description coverage;
- acceptance-criteria coverage;
- extracted-capability and prompt-specific required-concept coverage using the same morphology as
  runtime quality checks;
- issue codes and whether one revision was required;
- provider attempt count, prompt/output tokens, and provider duration when metrics are available.

The checked-in 30-case suite covers three-character briefs, explicit workflows, existing-ticket
planning, 120- and 200-ticket synthetic backlogs, irrelevant context, and prompt-injection text.
Live regression runs force temperature `0` and use seed `42` unless a seed is configured.

Run the same evaluation cases against the configured live Ollama model:

```bash
uv run python scripts/evaluate_planner.py
```

The command exits non-zero if any case remains below the quality gate after the bounded revision.
