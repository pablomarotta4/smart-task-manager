# Minimal-Input AI Planning Implementation Plan

> **For Codex:** Execute this plan task-by-task. If an `executing-plans` skill is installed, use it; otherwise follow the steps manually.

**Goal:** Turn minimal project-manager input into a useful editable backlog while keeping project context bounded, uncertainty visible, model calls observable, and failures explicit.

**Architecture:** Keep the bounded FastAPI/LangGraph service and Spring confirmation boundary. Compile a deterministic budgeted project view before prompting, skip the LLM analyzer for existing tickets, return optional non-blocking questions with the draft, and expose provider readiness and metrics without leaking prompt content.

**Tech Stack:** Python 3.12, FastAPI, Pydantic v2, LangGraph, httpx, pytest, Ruff, Pyright, Java 21, Spring Boot 3.4, JUnit 5, Mockito, React 19, Vitest, Testing Library, Vite.

**Files to Understand:**

- `ai-service/src/smart_task_ai/contracts.py` - Boundary schemas and validation.
- `ai-service/src/smart_task_ai/prompts.py` - Prompt assembly and current raw context serialization.
- `ai-service/src/smart_task_ai/planner.py` - Bounded graph and revision routing.
- `ai-service/src/smart_task_ai/ollama.py` - Structured provider calls and repair retry.
- `ai-service/src/smart_task_ai/quality.py` - Deterministic draft checks.
- `ai-service/src/smart_task_ai/evaluation.py` - Golden-set evaluation.
- `src/main/java/com/pablomarotta/smart_task_manager/dto/planning/ProjectPlanDraft.java` - Java draft contract.
- `frontend/components/DraftEditor.jsx` - Human review surface.

---

### Task 1: Version the minimal-input draft and prompt contracts

**Files:**

- Modify: `ai-service/tests/test_contracts.py`
- Modify: `ai-service/tests/test_planner.py`
- Modify: `ai-service/src/smart_task_ai/contracts.py`
- Modify: `ai-service/src/smart_task_ai/prompts.py`

**Steps:**

1. Add failing tests proving three-character prompts validate, drafts accept at most three
   `open_questions`, and minimal-input prompts demand conservative assumptions without blocking.
2. Run the focused tests and witness the expected validation/assertion failures.
3. Add `open_questions` to `ProjectDraft`, lower `PlanningRequest.prompt` to three characters, and
   update generation/revision prompt rules.
4. Run the focused tests and verify green.
5. Commit `feat(ai): support minimal planning briefs`.

### Task 2: Compile bounded existing-project context

**Files:**

- Create: `ai-service/src/smart_task_ai/context.py`
- Create: `ai-service/tests/test_context.py`
- Modify: `ai-service/src/smart_task_ai/prompts.py`
- Modify: `ai-service/src/smart_task_ai/planner.py`
- Modify: `ai-service/tests/test_planner.py`

**Steps:**

1. Add failing tests for full selected-ticket preservation, dependency/relevance ordering, compact
   sibling summaries, omitted counts, and a 200-task prompt remaining inside budget.
2. Run the tests and verify they fail because no context compiler exists.
3. Implement a pure deterministic compiler with an approximate token budget and explicit
   `ContextBudgetExceeded` failure.
4. Add failing planner tests proving existing tickets skip the LLM brief analyzer, derive mandatory
   capabilities from selected acceptance criteria, and use the ticket system prompt during revision.
5. Implement the focused existing-ticket graph behavior and run the focused suite green.
6. Commit `feat(ai): budget project planning context`.

### Task 3: Strengthen contextual quality checks

**Files:**

- Modify: `ai-service/tests/test_quality.py`
- Modify: `ai-service/src/smart_task_ai/quality.py`
- Modify: `ai-service/src/smart_task_ai/planner.py`

**Steps:**

1. Add failing tests for selected-ticket drift and duplication with existing non-selected work.
2. Run the tests and witness the missing quality issue codes.
3. Extend deterministic assessment with contextual alignment and duplicate-work checks while keeping
   minimal new-project assumptions valid.
4. Run quality and planner tests green.
5. Commit `feat(ai): validate contextual plan quality`.

### Task 4: Bound and observe Ollama calls

**Files:**

- Modify: `ai-service/tests/test_ollama.py`
- Modify: `ai-service/tests/test_api.py`
- Modify: `ai-service/src/smart_task_ai/providers.py`
- Modify: `ai-service/src/smart_task_ai/ollama.py`
- Modify: `ai-service/src/smart_task_ai/settings.py`
- Modify: `ai-service/src/smart_task_ai/api.py`

**Steps:**

1. Add failing tests for explicit context/output limits, fixed-seed support, safe per-call metrics,
   repair-attempt visibility, and configured-model readiness.
2. Run the focused tests and verify the expected failures.
3. Add typed phase/run metadata to the provider boundary, explicit Ollama options, safe metrics
   emission, and an async configured-model readiness probe.
4. Add `GET /ready` while preserving `GET /health` as liveness.
5. Run the provider/API tests green and commit `feat(ai): expose model readiness and call metrics`.

### Task 5: Upgrade behavioral evaluation

**Files:**

- Modify: `ai-service/tests/test_behavior_eval.py`
- Modify: `ai-service/src/smart_task_ai/evaluation.py`
- Modify: `ai-service/scripts/evaluate_planner.py`
- Modify: `ai-service/evals/cases.json`
- Modify: `ai-service/README.md`

**Steps:**

1. Add failing tests proving evaluation uses runtime capability morphology and reports latency,
   tokens, call count, and contextual cases.
2. Run the focused tests and witness the failures.
3. Reuse the runtime capability matcher, attach provider metrics by run ID, and make regression
   defaults deterministic.
4. Expand the checked-in golden set to at least thirty minimal, explicit, contextual, adversarial,
   and capacity cases.
5. Run deterministic tests green and commit `test(ai): expand planning behavior evaluation`.

### Task 6: Carry open questions through Spring

**Files:**

- Modify: `src/test/java/com/pablomarotta/smart_task_manager/client/FastApiAIPlanningClientTest.java`
- Modify: `src/test/java/com/pablomarotta/smart_task_manager/controller/ProjectGenerationControllerTest.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/dto/planning/ProjectPlanDraft.java`
- Modify: `src/main/java/com/pablomarotta/smart_task_manager/dto/planning/GenerateProjectRequest.java`

**Steps:**

1. Add failing contract/controller tests for `open_questions` deserialization, three-character prompts,
   and confirmation round-tripping the edited questions.
2. Run focused Maven tests and witness the expected failures.
3. Extend the records and validation without changing persistence ownership or confirmation behavior.
4. Run focused tests green and commit `feat(planning): carry draft questions`.

### Task 7: Present uncertainty in the draft review UI

**Files:**

- Modify: `frontend/App.test.jsx`
- Modify: `frontend/App.jsx`
- Modify: `frontend/components/DraftEditor.jsx`
- Modify: `frontend/styles.css`

**Steps:**

1. Add failing UI tests proving a three-character brief can generate and open questions appear as
   editable, non-blocking review fields included in confirmation.
2. Run the focused Vitest tests and witness the failures.
3. Lower the prompt gate, add accessible open-question editors, and explain that assumptions let the
   user continue immediately.
4. Run frontend tests and build green, then commit `feat(web): review ai planning questions`.

### Task 8: Documentation, live verification, review, and merge

**Files:**

- Modify: `README.md`
- Modify: `ai-service/README.md`

**Steps:**

1. Document context budgets, model readiness, deterministic evaluation, and the minimal-input
   planning contract.
2. Run all AI tests, Ruff, Pyright, Java non-database tests/package, frontend tests/build, and diff
   checks.
3. Start native Ollama and run focused live probes for a three-character brief, the exact frontend
   existing-ticket prompt, and a 200-ticket context; record call count, tokens, latency, and quality.
4. Fix every observed contract, behavior, truncation, or regression failure and repeat the affected
   gates.
5. Review the complete requirements against current evidence, commit documentation/fixes, and
   fast-forward merge the clean feature branch into `main` only when every required gate is proven.

