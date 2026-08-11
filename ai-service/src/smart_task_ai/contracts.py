from __future__ import annotations

from datetime import date
from enum import StrEnum
from typing import Annotated, Literal, Self
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

ShortText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=3, max_length=255)]
Description = Annotated[
    str, StringConstraints(strip_whitespace=True, min_length=20, max_length=2_000)
]
Criterion = Annotated[
    str, StringConstraints(strip_whitespace=True, min_length=10, max_length=500)
]
ClientId = Annotated[
    str,
    StringConstraints(
        strip_whitespace=True,
        to_lower=True,
        min_length=1,
        max_length=50,
        pattern=r"^[a-z][a-z0-9-]*$",
    ),
]
PositiveId = Annotated[int, Field(gt=0)]


def _empty_int_list() -> list[int]:
    return []


class ContractModel(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class Priority(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    URGENT = "URGENT"


class TicketDraft(ContractModel):
    client_id: ClientId
    title: Annotated[str, StringConstraints(min_length=5, max_length=120)]
    description: Description
    priority: Priority
    estimated_hours: Annotated[float, Field(ge=0.1, le=80)]
    acceptance_criteria: Annotated[list[Criterion], Field(min_length=1, max_length=8)]
    depends_on: Annotated[list[ClientId], Field(max_length=6)] = Field(default_factory=list)
    category: Annotated[str | None, StringConstraints(min_length=2, max_length=32)] = None
    due_in_days: Annotated[int | None, Field(ge=0, le=365)] = None

    @model_validator(mode="after")
    def validate_dependencies(self) -> Self:
        if self.client_id in self.depends_on:
            raise ValueError(f"ticket {self.client_id} cannot depend on itself")
        if len(self.depends_on) != len(set(self.depends_on)):
            raise ValueError(f"ticket {self.client_id} dependencies must be unique")
        return self


class ProjectDraft(ContractModel):
    name: Annotated[str, StringConstraints(min_length=3, max_length=150)]
    objective: Description
    assumptions: Annotated[list[ShortText], Field(max_length=10)] = Field(default_factory=list)
    risks: Annotated[list[ShortText], Field(max_length=10)] = Field(default_factory=list)
    tickets: Annotated[list[TicketDraft], Field(min_length=3, max_length=12)]

    @model_validator(mode="after")
    def validate_ticket_graph(self) -> Self:
        ticket_ids = [ticket.client_id for ticket in self.tickets]
        known_ids = set(ticket_ids)
        if len(ticket_ids) != len(known_ids):
            raise ValueError("ticket client IDs must be unique")

        for ticket in self.tickets:
            unknown = set(ticket.depends_on) - known_ids
            if unknown:
                raise ValueError(
                    f"ticket {ticket.client_id} depends on unknown ticket: {sorted(unknown)[0]}"
                )

        dependencies = {ticket.client_id: ticket.depends_on for ticket in self.tickets}
        visiting: set[str] = set()
        visited: set[str] = set()

        def visit(ticket_id: str) -> None:
            if ticket_id in visiting:
                raise ValueError("ticket dependencies contain a cycle")
            if ticket_id in visited:
                return
            visiting.add(ticket_id)
            for dependency in dependencies[ticket_id]:
                visit(dependency)
            visiting.remove(ticket_id)
            visited.add(ticket_id)

        for ticket_id in ticket_ids:
            visit(ticket_id)
        return self


class BriefAnalysis(ContractModel):
    explicit_capabilities: Annotated[list[ShortText], Field(min_length=1, max_length=12)]


class PlanningProjectContext(ContractModel):
    id: PositiveId
    name: Annotated[str, StringConstraints(min_length=3, max_length=150)]
    objective: Annotated[str | None, StringConstraints(max_length=2_000)] = None


class PlanningTaskContext(ContractModel):
    id: PositiveId
    title: Annotated[str, StringConstraints(min_length=1, max_length=255)]
    description: Annotated[str | None, StringConstraints(max_length=2_000)] = None
    status: Literal["TODO", "IN_PROGRESS", "BLOCKED", "DONE", "CANCELLED"]
    priority: Priority | None = None
    category: Annotated[str | None, StringConstraints(max_length=32)] = None
    due_date: date | None = None
    position: Annotated[int | None, Field(ge=0)] = None
    assignee_id: PositiveId | None = None
    assignee_username: Annotated[str | None, StringConstraints(max_length=50)] = None
    acceptance_criteria: Annotated[list[Criterion], Field(max_length=20)] = Field(
        default_factory=list
    )
    depends_on_task_ids: list[int] = Field(default_factory=_empty_int_list, max_length=20)


class PlanningContext(ContractModel):
    mode: Literal["EXISTING_TASK"]
    project: PlanningProjectContext
    selected_task_id: PositiveId
    tasks: Annotated[list[PlanningTaskContext], Field(min_length=1, max_length=200)]

    @model_validator(mode="after")
    def validate_selected_task(self) -> Self:
        task_ids = [task.id for task in self.tasks]
        if len(task_ids) != len(set(task_ids)):
            raise ValueError("project context task IDs must be unique")
        if self.selected_task_id not in task_ids:
            raise ValueError("selected task must be present in project context")
        known_ids = set(task_ids)
        for task in self.tasks:
            if any(dependency_id <= 0 for dependency_id in task.depends_on_task_ids):
                raise ValueError("task context dependency IDs must be positive")
            if not set(task.depends_on_task_ids).issubset(known_ids):
                raise ValueError("task context contains an unknown dependency")
        return self


class PlanningRequest(ContractModel):
    contract_version: Literal["v1"]
    run_id: UUID
    prompt: Annotated[str, StringConstraints(min_length=10, max_length=4_000)]
    context: PlanningContext | None = None


class QualityIssue(ContractModel):
    code: Annotated[str, StringConstraints(min_length=3, max_length=64)]
    message: Annotated[str, StringConstraints(min_length=5, max_length=500)]
    ticket_ids: list[ClientId] = Field(default_factory=list)


class QualityMetrics(ContractModel):
    ticket_count: Annotated[int, Field(ge=0)]
    unique_title_ratio: Annotated[float, Field(ge=0, le=1)]
    max_title_similarity: Annotated[float, Field(ge=0, le=1)]
    description_coverage: Annotated[float, Field(ge=0, le=1)]
    acceptance_criteria_coverage: Annotated[float, Field(ge=0, le=1)]


class QualityReport(ContractModel):
    score: Annotated[int, Field(ge=0, le=100)]
    passed: bool
    issues: list[QualityIssue]
    metrics: QualityMetrics


class PlanningResponse(ContractModel):
    contract_version: Literal["v1"] = "v1"
    run_id: UUID
    draft: ProjectDraft
    quality: QualityReport
    revision_count: Annotated[int, Field(ge=0, le=1)]
    model: Annotated[str, StringConstraints(min_length=1, max_length=100)]
