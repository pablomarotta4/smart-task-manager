# Minimal-Input AI Planning Design

- Status: Accepted
- Date: 2026-08-11
- Scope: Smart Task Manager AI planning, Spring contract, and draft-review UI

## Summary

The planning assistant must behave like a capable junior project manager: a short request such as
"launch a newsletter" should become a useful, editable first backlog without forcing the user
through an interview. The assistant fills non-critical gaps with conservative product assumptions,
keeps those assumptions visible, and may surface up to three non-blocking questions whose answers
would materially improve the plan.

The existing bounded LangGraph remains. This change makes its inputs smaller and more deliberate,
its output easier to review, and its provider behavior measurable. It does not add RAG, autonomous
tools, multiple agents, or direct AI writes.

## Product Rules

1. Minimal input produces a draft immediately.
2. Missing technical detail is handled as an explicit assumption, not an invented hidden fact.
3. Questions never block draft creation in this version. They identify decisions worth revisiting.
4. The selected ticket is always included in full for existing-ticket planning.
5. Sibling tickets prevent duplicated work but cannot crowd the selected ticket or output out of the
   model context window.
6. Spring remains the only writer and confirmation remains mandatory.

## Approaches Considered

### Blocking clarification before generation

This protects against ambiguity but conflicts with minimal-input planning and adds a new persisted
pause/resume lifecycle to every vague request.

### A separate LLM clarification interview

This can ask nuanced questions, but adds latency and cost before the user sees value. It also makes
the common path at least three calls.

### Immediate draft with assumptions and open questions

This is selected. It preserves a two-call new-project path and a one-call existing-ticket path while
keeping uncertainty visible during human review. A future blocking clarification state remains
possible if real usage shows decisions that cannot safely be represented as assumptions.

## Graph And Prompt Flow

```text
new project:      analyze brief (LLM) -> generate -> assess -> optional revision -> finalize
existing ticket:  derive criteria (code) -> generate -> assess -> optional revision -> finalize
```

New-project analysis continues to preserve explicitly requested user actions. Existing-ticket
analysis becomes deterministic: acceptance criteria from the selected ticket are the mandatory
capabilities. This removes a redundant full-context LLM call and prevents planning instructions such
as "make this actionable" from becoming false product requirements.

Generation prompts instruct the model to choose the smallest useful end-to-end workflow, state
reasonable defaults, avoid choosing a technology stack unless requested, and return zero to three
open questions. Revision uses the same new-project or existing-ticket system prompt as generation.

## Context Budget

The AI service compiles a prompt-specific context view rather than serializing all project data
twice. It contains:

- project identity and objective;
- the selected ticket in full;
- a small set of direct dependency and token-relevant active tickets with useful details;
- a compact title/status index for as many remaining tickets as fit;
- the number of omitted backlog items.

Selection is deterministic. The configured input budget is lower than the Ollama context window and
reserves space for structured output. The provider sends explicit `num_ctx` and `num_predict`
options, so capacity does not depend on an invisible server default. If the selected ticket and base
instructions alone cannot fit, the service fails explicitly instead of silently truncating them.

## Contracts And Review UI

`ProjectDraft` gains an optional `open_questions` list with at most three concise questions. The Java
contract maps it as `openQuestions`; the browser presents the questions above assumptions and lets
the user edit them. Questions are review metadata and are not persisted as tickets.

The prompt minimum becomes three characters across FastAPI, Spring validation, and React. A very
short noun is enough to request a first plan; the generated objective and tickets keep their current
strong validation limits.

## Quality And Evaluation

Structural checks remain deterministic. Existing-ticket assessment additionally verifies selected
ticket alignment and flags proposed tickets that duplicate non-selected project work. The live
evaluation reuses the runtime capability matcher so singular/plural variants do not disagree.

The golden set expands to at least thirty cases covering minimal briefs, explicit workflows,
existing-ticket context, large backlogs, irrelevant context, and prompt-injection text. Regression
runs use temperature zero and a fixed seed; exploratory robustness can still use the production
temperature.

## Observability And Readiness

Every Ollama boundary call records model, run ID, phase, attempt, prompt tokens, output tokens, and
provider duration without logging prompt or project content. Contract-repair attempts are visible.
The evaluation report aggregates call count, token totals, and latency.

`GET /health` remains a process liveness check. `GET /ready` verifies that Ollama is reachable and
that the configured model is installed, returning a safe readiness result instead of allowing a
later opaque 404.

## Non-Goals

- Vector search or RAG for project tickets
- Multi-agent planning
- A mandatory questionnaire
- AI writes to PostgreSQL
- Cross-project memory
- Streaming partial drafts

## Acceptance Criteria

- A three-character brief can produce a valid editable draft.
- The normal new-project path uses two LLM calls; the normal existing-ticket path uses one.
- A 200-ticket request compiles below the configured input budget while preserving the selected
  ticket.
- The exact frontend existing-ticket prompt no longer triggers a false capability revision.
- The draft can expose up to three non-blocking open questions.
- Existing-work duplication and selected-ticket drift are visible quality failures.
- Readiness reports a missing configured model before planning is attempted.
- Tests, static analysis, builds, and live model probes pass before merge.

