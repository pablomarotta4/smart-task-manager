from __future__ import annotations

import importlib
import json

import pytest

from smart_task_ai.contracts import (
    PlanningContext,
    PlanningProjectContext,
    PlanningTaskContext,
    Priority,
)


def project_context(task_count: int = 6, *, selected_task_id: int = 3) -> PlanningContext:
    tasks = [
        PlanningTaskContext(
            id=index,
            title=(
                "Deliver email reminders" if index == selected_task_id else f"Backlog item {index}"
            ),
            description=(
                "Notify assignees one day before due dates and once per day while overdue."
                if index == selected_task_id
                else (
                    f"Maintain unrelated reporting workflow {index} with validation "
                    "and audit history."
                )
            ),
            status="TODO" if index % 4 else "DONE",
            priority=Priority.HIGH if index == selected_task_id else Priority.MEDIUM,
            category="REMINDERS"
            if index in {selected_task_id, selected_task_id + 1}
            else "REPORTING",
            position=index - 1,
            acceptance_criteria=(
                [
                    "A reminder is sent one day before the due date",
                    "An overdue reminder is sent no more than once per day",
                ]
                if index == selected_task_id
                else [f"Reporting workflow {index} is covered by an observable acceptance check"]
            ),
            depends_on_task_ids=[selected_task_id - 1] if index == selected_task_id else [],
        )
        for index in range(1, task_count + 1)
    ]
    return PlanningContext(
        mode="EXISTING_TASK",
        project=PlanningProjectContext(
            id=10,
            name="Operations Workspace",
            objective="Coordinate internal work without duplicating delivery.",
        ),
        selected_task_id=selected_task_id,
        tasks=tasks,
    )


def context_api():  # type: ignore[no-untyped-def]
    return importlib.import_module("smart_task_ai.context")


def test_compiler_preserves_selected_ticket_and_prioritizes_related_work() -> None:
    context = project_context()

    compiled = context_api().compile_planning_context(
        context,
        planning_prompt="Create a safe implementation plan for reminders",
        max_tokens=1_600,
    )
    payload = json.loads(compiled.context_json)

    assert payload["selected_task"]["id"] == 3
    assert payload["selected_task"]["description"] == context.tasks[2].description
    assert payload["selected_task"]["acceptance_criteria"] == context.tasks[2].acceptance_criteria
    assert 2 in compiled.detailed_task_ids
    assert 4 in compiled.detailed_task_ids


def test_compiler_keeps_unrelated_siblings_in_compact_index() -> None:
    context = project_context()

    compiled = context_api().compile_planning_context(
        context,
        planning_prompt="Create a safe implementation plan for reminders",
        max_tokens=1_600,
    )
    payload = json.loads(compiled.context_json)

    assert 1 not in compiled.detailed_task_ids
    assert 1 in compiled.indexed_task_ids
    assert payload["existing_work_do_not_repeat"][0]["id"] == 1
    assert "backlog_index" not in payload


def test_compiler_keeps_two_hundred_task_project_inside_budget() -> None:
    context = project_context(200, selected_task_id=100)

    compiled = context_api().compile_planning_context(
        context,
        planning_prompt="Break the selected ticket into an actionable implementation plan",
        max_tokens=1_600,
    )
    payload = json.loads(compiled.context_json)

    assert compiled.estimated_tokens <= 1_600
    assert payload["selected_task"]["id"] == 100
    assert compiled.omitted_task_count > 0
    assert len(compiled.detailed_task_ids) <= 8


def test_compiler_reports_when_selected_ticket_cannot_fit() -> None:
    context = project_context()

    with pytest.raises(ValueError, match="selected ticket"):
        context_api().compile_planning_context(
            context,
            planning_prompt="Plan reminders",
            max_tokens=20,
        )


def test_generation_prompt_including_large_context_stays_inside_total_budget() -> None:
    prompts = importlib.import_module("smart_task_ai.prompts")
    context = project_context(200, selected_task_id=100)

    prompt = prompts.generation_prompt(
        "Break the selected ticket into an actionable implementation plan",
        context=context,
        max_input_tokens=2_500,
    )

    assert prompts.estimate_tokens(prompts.TASK_PLANNER_SYSTEM_PROMPT + prompt) <= 2_500
    assert '"id":100' in prompt
    assert "omitted_task_count" in prompt
