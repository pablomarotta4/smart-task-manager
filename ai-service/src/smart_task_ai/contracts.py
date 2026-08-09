from __future__ import annotations

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


class PlanningRequest(ContractModel):
    contract_version: Literal["v1"]
    run_id: UUID
    prompt: Annotated[str, StringConstraints(min_length=10, max_length=4_000)]


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
