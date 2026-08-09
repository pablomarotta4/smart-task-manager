from __future__ import annotations

from uuid import uuid4

import pytest
from pydantic import ValidationError

from smart_task_ai.contracts import PlanningRequest, Priority, ProjectDraft, TicketDraft


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


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("contract_version", "v2"),
        ("prompt", "short"),
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
