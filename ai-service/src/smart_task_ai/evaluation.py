from __future__ import annotations

import re
from typing import Annotated, Protocol
from uuid import UUID, uuid4

from pydantic import Field, StringConstraints

from smart_task_ai.contracts import ContractModel, PlanningResponse


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
    prompt: Annotated[str, StringConstraints(min_length=10, max_length=4_000)]
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


class EvaluationSummary(ContractModel):
    total_cases: int
    passed_cases: int
    pass_rate: float
    revised_cases: int
    average_score: float
    results: list[EvaluationResult]


class Planner(Protocol):
    async def plan(self, *, run_id: UUID, prompt: str) -> PlanningResponse: ...


def normalized_tokens(text: str) -> set[str]:
    return set(re.findall(r"[a-z0-9]+", text.casefold()))


async def evaluate_cases(
    cases: list[EvaluationCase],
    planner: Planner,
) -> EvaluationSummary:
    results: list[EvaluationResult] = []
    for case in cases:
        response = await planner.plan(run_id=uuid4(), prompt=case.prompt)
        metrics = response.quality.metrics
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
                normalized_tokens(term) <= ticket_tokens
                for term in concept.any_of
                for ticket_tokens in ticket_token_sets
            )
        ]
        passed = (
            response.quality.passed
            and response.quality.score >= case.min_score
            and metrics.ticket_count >= case.min_tickets
            and metrics.unique_title_ratio == 1
            and metrics.description_coverage == 1
            and metrics.acceptance_criteria_coverage == 1
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
                ticket_count=metrics.ticket_count,
                unique_title_ratio=metrics.unique_title_ratio,
                description_coverage=metrics.description_coverage,
                acceptance_criteria_coverage=metrics.acceptance_criteria_coverage,
                revision_count=response.revision_count,
                missing_concepts=missing_concepts,
            )
        )

    total_cases = len(results)
    passed_cases = sum(result.passed for result in results)
    return EvaluationSummary(
        total_cases=total_cases,
        passed_cases=passed_cases,
        pass_rate=passed_cases / total_cases if total_cases else 0,
        revised_cases=sum(result.revision_count > 0 for result in results),
        average_score=(
            sum(result.score for result in results) / total_cases if total_cases else 0
        ),
        results=results,
    )
