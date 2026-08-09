from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, field
from uuid import uuid4

from smart_task_ai.contracts import Priority, ProjectDraft, TicketDraft
from smart_task_ai.planner import ProjectPlanner


def ticket(client_id: str, title: str, *, rich: bool = True) -> TicketDraft:
    return TicketDraft(
        client_id=client_id,
        title=title,
        description=(
            f"Deliver {title.lower()} as a complete vertical slice with validation, useful error "
            "messages, instrumentation, and automated tests."
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


def plan(tickets: list[TicketDraft]) -> ProjectDraft:
    return ProjectDraft(
        name="Community Garden Planner",
        objective="Help neighbors coordinate plots, planting work, and shared garden supplies.",
        assumptions=["The initial community has fewer than fifty members"],
        risks=["Planting dates vary with local weather"],
        tickets=tickets,
    )


def good_plan(prefix: str = "") -> ProjectDraft:
    return plan(
        [
            ticket(f"{prefix}setup", "Set up garden membership"),
            ticket(f"{prefix}plots", "Assign members to garden plots"),
            ticket(f"{prefix}tasks", "Schedule shared gardening work"),
            ticket(f"{prefix}supplies", "Track communal garden supplies"),
        ]
    )


def repetitive_plan() -> ProjectDraft:
    return plan(
        [
            ticket("one", "Create garden task", rich=False),
            ticket("two", "Create garden task", rich=False),
            ticket("three", "Create garden task", rich=False),
        ]
    )


@dataclass
class ScriptedModel:
    responses: Sequence[ProjectDraft]
    model_name: str = "scripted-test-model"
    calls: list[tuple[str, str]] = field(default_factory=lambda: list[tuple[str, str]]())

    async def generate(self, *, system_prompt: str, user_prompt: str) -> ProjectDraft:
        self.calls.append((system_prompt, user_prompt))
        index = min(len(self.calls) - 1, len(self.responses) - 1)
        return self.responses[index]


async def test_adequate_first_output_finishes_without_revision() -> None:
    model = ScriptedModel([good_plan()])
    planner = ProjectPlanner(model)

    result = await planner.plan(run_id=uuid4(), prompt="Build a community garden planner")

    assert result.quality.passed is True
    assert result.revision_count == 0
    assert result.draft == good_plan()
    assert len(model.calls) == 1


async def test_repetitive_output_is_revised_once_with_quality_feedback() -> None:
    model = ScriptedModel([repetitive_plan(), good_plan()])
    planner = ProjectPlanner(model)

    result = await planner.plan(run_id=uuid4(), prompt="Build a community garden planner")

    assert result.quality.passed is True
    assert result.revision_count == 1
    assert result.draft == good_plan()
    assert len(model.calls) == 2
    assert "duplicate_titles" in model.calls[1][1]
    assert "thin_descriptions" in model.calls[1][1]


async def test_persistently_weak_output_stops_after_one_revision() -> None:
    model = ScriptedModel([repetitive_plan(), repetitive_plan(), good_plan()])
    planner = ProjectPlanner(model)

    result = await planner.plan(run_id=uuid4(), prompt="Build a community garden planner")

    assert result.quality.passed is False
    assert result.revision_count == 1
    assert len(model.calls) == 2


async def test_independent_runs_do_not_share_prompt_or_revision_state() -> None:
    model = ScriptedModel([good_plan("first-"), good_plan("second-")])
    planner = ProjectPlanner(model)

    first = await planner.plan(run_id=uuid4(), prompt="Plan the first garden")
    second = await planner.plan(run_id=uuid4(), prompt="Plan the second garden")

    assert first.revision_count == 0
    assert second.revision_count == 0
    assert len(model.calls) == 2
    assert "first garden" in model.calls[0][1]
    assert "second garden" in model.calls[1][1]
