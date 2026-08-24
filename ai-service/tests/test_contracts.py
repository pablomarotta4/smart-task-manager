from __future__ import annotations

from uuid import uuid4

import pytest
from pydantic import ValidationError

from smart_task_ai.contracts import (
    PlanningContext,
    PlanningProjectContext,
    PlanningRequest,
    PlanningTaskContext,
    Priority,
    ProjectDraft,
    TicketDraft,
)


def ticket(client_id: str, *, depends_on: list[str] | None = None) -> TicketDraft:
    return TicketDraft(
        client_id=client_id,
        title=f"Implement {client_id}",
        description=f"Deliver the complete implementation for {client_id} with tests.",
        priority=Priority.MEDIUM,
        estimated_hours=4,
        acceptance_criteria=[f"The {client_id} behavior is covered by automated tests"],
        depends_on=depends_on or [],
    )


def draft(tickets: list[TicketDraft] | None = None) -> ProjectDraft:
    return ProjectDraft(
        name="Household Budget App",
        objective="Help a household record spending and understand its monthly budget.",
        assumptions=["The first release supports one household"],
        risks=["Receipt quality may vary"],
        tickets=tickets
        or [ticket("setup"), ticket("expenses", depends_on=["setup"]), ticket("summary")],
    )


def test_accepts_a_strict_versioned_request_and_project_draft() -> None:
    request = PlanningRequest(contract_version="v1", run_id=uuid4(), prompt="Build a budget app")

    assert request.contract_version == "v1"
    assert len(draft().tickets) == 3


def test_accepts_a_three_character_brief() -> None:
    request = PlanningRequest(contract_version="v1", run_id=uuid4(), prompt="CRM")

    assert request.prompt == "CRM"


def test_accepts_at_most_three_non_blocking_open_questions() -> None:
    project_draft = draft().model_copy(
        update={
            "open_questions": [
                "Is this for one household or several?",
                "Should the first release import bank transactions?",
                "Which currency should the first release support?",
            ]
        }
    )

    validated = ProjectDraft.model_validate(project_draft.model_dump())

    assert len(validated.open_questions) == 3

    invalid = project_draft.model_dump()
    invalid["open_questions"].append("Should budgets be shared?")
    with pytest.raises(ValidationError, match="at most 3"):
        ProjectDraft.model_validate(invalid)


def test_accepts_existing_task_context_and_rejects_an_unknown_selected_task() -> None:
    context = PlanningContext(
        mode="EXISTING_TASK",
        project=PlanningProjectContext(
            id=20,
            name="Job Application Tracker",
            objective="Track opportunities through offer decisions.",
        ),
        selected_task_id=201,
        tasks=[
            PlanningTaskContext(
                id=201,
                title="Capture opportunity",
                description="Record company, role, source, and application link.",
                status="TODO",
                priority=Priority.HIGH,
                position=0,
                acceptance_criteria=["Every opportunity records its source"],
                depends_on_task_ids=[],
            )
        ],
    )

    request = PlanningRequest(
        contract_version="v1",
        run_id=uuid4(),
        prompt="Break this ticket into an actionable plan",
        context=context,
    )

    assert request.context is not None
    assert request.context.selected_task_id == 201
    invalid = context.model_dump(mode="json")
    invalid["selected_task_id"] = 999
    with pytest.raises(ValidationError, match="selected task"):
        PlanningContext.model_validate(invalid)


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("contract_version", "v2"),
        ("prompt", "no"),
        ("unexpected", True),
    ],
)
def test_rejects_invalid_or_extra_request_fields(field: str, value: object) -> None:
    payload: dict[str, object] = {
        "contract_version": "v1",
        "run_id": str(uuid4()),
        "prompt": "Build a useful budget application",
    }
    payload[field] = value

    with pytest.raises(ValidationError):
        PlanningRequest.model_validate(payload)


def test_rejects_fewer_than_three_tickets() -> None:
    with pytest.raises(ValidationError, match="at least 3"):
        draft([ticket("one"), ticket("two")])


def test_rejects_duplicate_ticket_client_ids() -> None:
    with pytest.raises(ValidationError, match="unique"):
        draft([ticket("same"), ticket("same"), ticket("other")])


def test_rejects_unknown_and_self_dependencies() -> None:
    with pytest.raises(ValidationError, match="unknown ticket"):
        draft([ticket("one", depends_on=["missing"]), ticket("two"), ticket("three")])

    with pytest.raises(ValidationError, match="cannot depend on itself"):
        draft([ticket("one", depends_on=["one"]), ticket("two"), ticket("three")])


def test_rejects_dependency_cycles() -> None:
    with pytest.raises(ValidationError, match="cycle"):
        draft(
            [
                ticket("one", depends_on=["three"]),
                ticket("two", depends_on=["one"]),
                ticket("three", depends_on=["two"]),
            ]
        )


def test_rejects_invalid_priority_and_thin_ticket_content() -> None:
    payload = ticket("valid").model_dump(mode="json")
    payload.update(priority="CRITICAL", description="too short", acceptance_criteria=[])

    with pytest.raises(ValidationError):
        TicketDraft.model_validate(payload)


def test_rejects_estimates_below_the_confirmation_contract_minimum() -> None:
    payload = ticket("valid").model_dump(mode="json")
    payload["estimated_hours"] = 0.05

    with pytest.raises(ValidationError):
        TicketDraft.model_validate(payload)
