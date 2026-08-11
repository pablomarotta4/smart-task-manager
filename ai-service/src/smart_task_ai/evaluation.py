from __future__ import annotations

from typing import Annotated, Protocol
from uuid import UUID, uuid4

from pydantic import Field, StringConstraints

from smart_task_ai.contracts import (
    ContractModel,
    PlanningContext,
    PlanningResponse,
    PlanningTaskContext,
)
from smart_task_ai.providers import ModelMetricsReader
from smart_task_ai.quality import capability_matches_ticket, normalized_tokens


class ExpectedConcept(ContractModel):
    name: Annotated[str, StringConstraints(min_length=2, max_length=80)]
    any_of: Annotated[
        list[Annotated[str, StringConstraints(min_length=2, max_length=120)]],
        Field(min_length=1, max_length=8),
    ]


def empty_expected_concepts() -> list[ExpectedConcept]:
    return []


class EvaluationCase(ContractModel):
    id: Annotated[
        str,
        StringConstraints(pattern=r"^[a-z0-9][a-z0-9-]*$", min_length=2, max_length=80),
    ]
    prompt: Annotated[str, StringConstraints(min_length=3, max_length=4_000)]
    context: PlanningContext | None = None
    backlog_size: Annotated[int | None, Field(ge=1, le=200)] = None
    min_tickets: Annotated[int, Field(ge=3, le=12)] = 3
    min_score: Annotated[int, Field(ge=0, le=100)] = 75
    required_concepts: Annotated[list[ExpectedConcept], Field(max_length=12)] = Field(
        default_factory=empty_expected_concepts
    )


class EvaluationResult(ContractModel):
    case_id: str
    passed: bool
    score: int
    issue_codes: list[str]
    ticket_count: int
    unique_title_ratio: float
    description_coverage: float
    acceptance_criteria_coverage: float
    revision_count: int
    missing_concepts: list[str]
    call_count: Annotated[int, Field(ge=0)]
    prompt_tokens: Annotated[int, Field(ge=0)]
    output_tokens: Annotated[int, Field(ge=0)]
    provider_duration_ms: Annotated[float, Field(ge=0)]


class EvaluationSummary(ContractModel):
    total_cases: int
    passed_cases: int
    pass_rate: float
    revised_cases: int
    average_score: float
    total_calls: Annotated[int, Field(ge=0)]
    total_prompt_tokens: Annotated[int, Field(ge=0)]
    total_output_tokens: Annotated[int, Field(ge=0)]
    total_provider_duration_ms: Annotated[float, Field(ge=0)]
    results: list[EvaluationResult]


class Planner(Protocol):
    async def plan(
        self,
        *,
        run_id: UUID,
        prompt: str,
        context: PlanningContext | None = None,
    ) -> PlanningResponse: ...


def _expanded_context(case: EvaluationCase) -> PlanningContext | None:
    if case.context is None or case.backlog_size is None:
        return case.context
    if case.backlog_size <= len(case.context.tasks):
        return case.context

    tasks = list(case.context.tasks)
    next_id = max(task.id for task in tasks) + 1
    while len(tasks) < case.backlog_size:
        tasks.append(
            PlanningTaskContext(
                id=next_id,
                title=f"Unrelated backlog item {next_id}",
                status="TODO",
                category="BACKLOG",
                position=len(tasks),
            )
        )
        next_id += 1
    return case.context.model_copy(update={"tasks": tasks})


async def evaluate_cases(
    cases: list[EvaluationCase],
    planner: Planner,
    *,
    metrics_reader: ModelMetricsReader | None = None,
) -> EvaluationSummary:
    results: list[EvaluationResult] = []
    for case in cases:
        run_id = uuid4()
        response = await planner.plan(
            run_id=run_id,
            prompt=case.prompt,
            context=_expanded_context(case),
        )
        quality_metrics = response.quality.metrics
        ticket_token_sets = [
            normalized_tokens(
                "\n".join((ticket.title, ticket.description, *ticket.acceptance_criteria))
            )
            for ticket in response.draft.tickets
        ]
        missing_concepts = [
            concept.name
            for concept in case.required_concepts
            if not any(
                capability_matches_ticket(term, ticket_tokens)
                for term in concept.any_of
                for ticket_tokens in ticket_token_sets
            )
        ]
        call_metrics = metrics_reader.metrics_for_run(run_id) if metrics_reader else []
        passed = (
            response.quality.passed
            and response.quality.score >= case.min_score
            and quality_metrics.ticket_count >= case.min_tickets
            and quality_metrics.unique_title_ratio == 1
            and quality_metrics.description_coverage == 1
            and quality_metrics.acceptance_criteria_coverage == 1
            and not missing_concepts
        )
        issue_codes = [issue.code for issue in response.quality.issues]
        if missing_concepts:
            issue_codes.append("missing_required_concepts")
        results.append(
            EvaluationResult(
                case_id=case.id,
                passed=passed,
                score=response.quality.score,
                issue_codes=issue_codes,
                ticket_count=quality_metrics.ticket_count,
                unique_title_ratio=quality_metrics.unique_title_ratio,
                description_coverage=quality_metrics.description_coverage,
                acceptance_criteria_coverage=quality_metrics.acceptance_criteria_coverage,
                revision_count=response.revision_count,
                missing_concepts=missing_concepts,
                call_count=len(call_metrics),
                prompt_tokens=sum(metric.prompt_tokens for metric in call_metrics),
                output_tokens=sum(metric.output_tokens for metric in call_metrics),
                provider_duration_ms=sum(metric.duration_ms for metric in call_metrics),
            )
        )

    total_cases = len(results)
    passed_cases = sum(result.passed for result in results)
    return EvaluationSummary(
        total_cases=total_cases,
        passed_cases=passed_cases,
        pass_rate=passed_cases / total_cases if total_cases else 0,
        revised_cases=sum(result.revision_count > 0 for result in results),
        average_score=(sum(result.score for result in results) / total_cases if total_cases else 0),
        total_calls=sum(result.call_count for result in results),
        total_prompt_tokens=sum(result.prompt_tokens for result in results),
        total_output_tokens=sum(result.output_tokens for result in results),
        total_provider_duration_ms=sum(result.provider_duration_ms for result in results),
        results=results,
    )
