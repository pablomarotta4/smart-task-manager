from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, field
from uuid import UUID

from smart_task_ai.contracts import (
    PlanningContext,
    PlanningProjectContext,
    PlanningTaskContext,
    Priority,
    ProjectDraft,
    TicketDraft,
)
from smart_task_ai.evaluation import EvaluationCase, ExpectedConcept, evaluate_cases
from smart_task_ai.planner import ProjectPlanner
from smart_task_ai.providers import ModelCallMetadata, ModelCallMetric


def ticket(client_id: str, title: str, *, rich: bool = True) -> TicketDraft:
    return TicketDraft(
        client_id=client_id,
        title=title,
        description=(
            f"Deliver {title.lower()} as a complete vertical slice with validation, clear errors, "
            "operational visibility, and automated tests."
            if rich
            else f"Implement {title.lower()} now."
        ),
        priority=Priority.MEDIUM,
        estimated_hours=4,
        acceptance_criteria=(
            [
                f"A user can complete {title.lower()} successfully",
                f"Automated tests cover failures in {title.lower()}",
            ]
            if rich
            else [f"The {title.lower()} behavior works"]
        ),
    )


def draft(name: str, tickets: list[TicketDraft]) -> ProjectDraft:
    return ProjectDraft(
        name=name,
        objective=f"Deliver a useful first version of {name} with a clear end-to-end workflow.",
        assumptions=["The first release serves a small user group"],
        risks=["Initial user feedback may change ticket ordering"],
        tickets=tickets,
    )


def good_draft(name: str) -> ProjectDraft:
    return draft(
        name,
        [
            ticket("foundation", "Establish the secure project foundation"),
            ticket("workflow", "Deliver the primary user workflow"),
            ticket("feedback", "Handle errors and user feedback"),
            ticket("measure", "Measure first-release outcomes"),
        ],
    )


def repetitive_draft() -> ProjectDraft:
    return draft(
        "Personal Meal Planner",
        [
            ticket("one", "Create meal feature", rich=False),
            ticket("two", "Create meal feature", rich=False),
            ticket("three", "Create meal feature", rich=False),
        ],
    )


@dataclass
class ScriptedModel:
    responses: Sequence[ProjectDraft]
    model_name: str = "behavior-fixture"
    call_count: int = 0
    metadata_calls: list[ModelCallMetadata | None] = field(
        default_factory=lambda: list[ModelCallMetadata | None]()
    )
    user_prompts: list[str] = field(default_factory=lambda: list[str]())

    async def generate(
        self,
        *,
        system_prompt: str,
        user_prompt: str,
        metadata: ModelCallMetadata | None = None,
    ) -> ProjectDraft:
        del system_prompt
        self.metadata_calls.append(metadata)
        self.user_prompts.append(user_prompt)
        index = min(self.call_count, len(self.responses) - 1)
        self.call_count += 1
        return self.responses[index]


@dataclass
class FixedMetrics:
    calls_per_run: int = 2

    def metrics_for_run(self, run_id: UUID) -> list[ModelCallMetric]:
        return [
            ModelCallMetric(
                run_id=run_id,
                model="behavior-fixture",
                phase="brief_analysis" if attempt == 1 else "generation",
                attempt=1,
                prompt_tokens=100 * attempt,
                output_tokens=20 * attempt,
                duration_ms=250 * attempt,
                outcome="success",
            )
            for attempt in range(1, self.calls_per_run + 1)
        ]


async def test_behavior_suite_measures_sufficiency_repetition_and_revision() -> None:
    cases = [
        EvaluationCase(id="meal-planner", prompt="Build a personal meal planning app"),
        EvaluationCase(id="repair-shop", prompt="Organize a small repair shop workflow"),
        EvaluationCase(id="status-api", prompt="Build a technical service status API"),
    ]
    model = ScriptedModel(
        [
            repetitive_draft(),
            good_draft("Personal Meal Planner"),
            good_draft("Repair Shop Workflow"),
            good_draft("Service Status API"),
        ]
    )

    summary = await evaluate_cases(cases, ProjectPlanner(model))

    assert summary.total_cases == 3
    assert summary.passed_cases == 3
    assert summary.pass_rate == 1
    assert summary.revised_cases == 1
    assert summary.average_score == 100
    assert all(result.ticket_count >= 3 for result in summary.results)
    assert all(result.unique_title_ratio == 1 for result in summary.results)
    assert model.call_count == 4


async def test_behavior_suite_keeps_persistently_weak_output_visible() -> None:
    cases = [EvaluationCase(id="weak", prompt="Build a useful personal meal planning app")]
    model = ScriptedModel([repetitive_draft(), repetitive_draft()])

    summary = await evaluate_cases(cases, ProjectPlanner(model))

    assert summary.passed_cases == 0
    assert summary.results[0].passed is False
    assert "duplicate_titles" in summary.results[0].issue_codes
    assert summary.results[0].revision_count == 1
    assert model.call_count == 2


async def test_behavior_suite_fails_when_an_explicit_capability_is_missing() -> None:
    cases = [
        EvaluationCase(
            id="tool-library",
            prompt="Let residents list, reserve, borrow, and return shared tools",
            required_concepts=[
                ExpectedConcept(
                    name="borrowing workflow", any_of=["borrow tool", "check out tool"]
                ),
                ExpectedConcept(
                    name="return workflow",
                    any_of=["tool return", "mark a tool as returned", "check in tool"],
                ),
            ],
        )
    ]
    model = ScriptedModel([good_draft("Tool Library")])

    summary = await evaluate_cases(cases, ProjectPlanner(model))

    assert summary.passed_cases == 0
    assert summary.results[0].passed is False
    assert summary.results[0].missing_concepts == ["borrowing workflow", "return workflow"]
    assert "missing_required_concepts" in summary.results[0].issue_codes


async def test_concept_matching_tolerates_punctuation_and_intervening_words() -> None:
    covered = good_draft("Tool Library")
    covered.tickets[0].title = "Facilitate Tool Borrowing Process"
    covered.tickets[0].description = (
        "Allow a resident to (borrow) a shared tool after reservation and record the borrower, "
        "checkout time, and expected return date."
    )
    cases = [
        EvaluationCase(
            id="tool-borrowing",
            prompt="Let residents borrow shared tools from a neighborhood library",
            required_concepts=[
                ExpectedConcept(name="borrowing workflow", any_of=["borrow a tool"])
            ],
        )
    ]

    summary = await evaluate_cases(cases, ProjectPlanner(ScriptedModel([covered])))

    assert summary.passed_cases == 1
    assert summary.results[0].missing_concepts == []


async def test_evaluation_reuses_runtime_morphology_for_required_concepts() -> None:
    covered = good_draft("Tool Library")
    covered.tickets[0].title = "Record a returned tool"
    covered.tickets[
        0
    ].description = (
        "Record that a resident returned one shared tool and make it available for reservations."
    )
    cases = [
        EvaluationCase(
            id="tool-return",
            prompt="Track tool returns",
            required_concepts=[ExpectedConcept(name="return workflow", any_of=["return tools"])],
        )
    ]

    summary = await evaluate_cases(cases, ProjectPlanner(ScriptedModel([covered])))

    assert summary.results[0].missing_concepts == []


async def test_evaluation_reports_provider_call_token_and_latency_metrics() -> None:
    metrics = FixedMetrics()

    summary = await evaluate_cases(
        [EvaluationCase(id="crm", prompt="CRM")],
        ProjectPlanner(ScriptedModel([good_draft("CRM")])),
        metrics_reader=metrics,
    )

    result = summary.results[0]
    assert result.call_count == 2
    assert result.prompt_tokens == 300
    assert result.output_tokens == 60
    assert result.provider_duration_ms == 750
    assert summary.total_calls == 2
    assert summary.total_prompt_tokens == 300
    assert summary.total_output_tokens == 60
    assert summary.total_provider_duration_ms == 750


async def test_evaluation_passes_existing_ticket_context_to_the_planner() -> None:
    context = PlanningContext(
        mode="EXISTING_TASK",
        project=PlanningProjectContext(id=1, name="Launch Workspace"),
        selected_task_id=10,
        tasks=[
            PlanningTaskContext(
                id=10,
                title="Deliver the primary user workflow",
                status="TODO",
            ),
            PlanningTaskContext(
                id=11,
                title="Measure first-release outcomes",
                status="IN_PROGRESS",
            ),
        ],
    )
    model = ScriptedModel([good_draft("Launch Workspace")])

    summary = await evaluate_cases(
        [
            EvaluationCase(
                id="existing-work",
                prompt="Plan this",
                context=context,
                backlog_size=200,
            )
        ],
        ProjectPlanner(model),
    )

    assert summary.results[0].passed is False
    assert "duplicates_existing_work" in summary.results[0].issue_codes
    assert model.call_count == 2
    assert '"omitted_task_count":' in model.user_prompts[0]
