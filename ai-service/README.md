# Smart Task AI

Internal FastAPI and LangGraph service that turns a short project brief into an editable,
quality-scored project draft. It never writes projects or tickets; the Spring API owns human
confirmation and persistence.

## Graph

```text
analyze brief -> generate -> assess -> (passed) finalize
                              (failed) revise -> assess -> finalize
```

Analysis extracts the brief's explicit capability checklist. Assessment checks structure,
repetition, actionability, and whether each checklist action appears in ticket content. Revision is
capped at one model call. A still-weak plan is returned with `quality.passed=false`, so a caller can
show the problem instead of mistaking valid JSON for a useful plan. An incomplete structured provider
response receives one contract-repair retry; it does not consume the quality-revision budget.

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

## Behavior evaluation

The deterministic test suite measures whether responses are enough and whether tickets repeat:

- number of tickets;
- unique normalized-title ratio;
- maximum pairwise title similarity;
- actionable-description coverage;
- acceptance-criteria coverage;
- extracted-capability and prompt-specific required-concept coverage;
- issue codes and whether one revision was required.

Run the same evaluation cases against the configured live Ollama model:

```bash
uv run python scripts/evaluate_planner.py
```

The command exits non-zero if any case remains below the quality gate after the bounded revision.
