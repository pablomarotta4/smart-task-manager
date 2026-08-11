from __future__ import annotations

import json
import math
import re
from dataclasses import dataclass

from smart_task_ai.contracts import PlanningContext, PlanningTaskContext

_MAX_DETAILED_TASKS = 8
_TOKEN_PATTERN = re.compile(r"[a-z0-9]+")
_STOP_WORDS = {
    "a",
    "an",
    "and",
    "as",
    "at",
    "be",
    "by",
    "for",
    "from",
    "in",
    "into",
    "is",
    "it",
    "of",
    "on",
    "or",
    "plan",
    "selected",
    "task",
    "the",
    "this",
    "to",
    "with",
}


@dataclass(frozen=True, slots=True)
class CompiledPlanningContext:
    context_json: str
    estimated_tokens: int
    detailed_task_ids: tuple[int, ...]
    indexed_task_ids: tuple[int, ...]
    omitted_task_count: int


def estimate_tokens(text: str) -> int:
    """Return a conservative-enough, provider-independent token estimate."""
    return max(1, math.ceil(len(text) / 4))


def _terms(value: str | None) -> set[str]:
    if not value:
        return set()
    return {
        token
        for token in _TOKEN_PATTERN.findall(value.casefold())
        if len(token) > 2 and token not in _STOP_WORDS
    }


def _task_terms(task: PlanningTaskContext) -> set[str]:
    values = [task.title, task.description, task.category, *task.acceptance_criteria]
    terms: set[str] = set()
    for value in values:
        terms.update(_terms(value))
    return terms


def _task_order(
    task: PlanningTaskContext,
    *,
    selected: PlanningTaskContext,
    focus_terms: set[str],
) -> tuple[int, int, int, int, int]:
    directly_related = (
        task.id in selected.depends_on_task_ids or selected.id in task.depends_on_task_ids
    )
    overlap = len(_task_terms(task) & focus_terms)
    same_category = bool(selected.category and task.category == selected.category)
    active = task.status not in {"DONE", "CANCELLED"}
    position = task.position if task.position is not None else task.id
    return (
        int(directly_related),
        overlap,
        int(same_category),
        int(active),
        -position,
    )


def _full_task(task: PlanningTaskContext) -> dict[str, object]:
    return task.model_dump(mode="json", exclude_none=True)


def _compact_task(task: PlanningTaskContext) -> dict[str, object]:
    compact: dict[str, object] = {
        "id": task.id,
        "title": task.title,
        "status": task.status,
    }
    if task.priority is not None:
        compact["priority"] = task.priority.value
    if task.category is not None:
        compact["category"] = task.category
    return compact


def _serialize(payload: dict[str, object]) -> str:
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":"))


def compile_planning_context(
    context: PlanningContext,
    *,
    planning_prompt: str,
    max_tokens: int,
) -> CompiledPlanningContext:
    """Compile project data by relevance while always preserving the selected ticket."""
    if max_tokens <= 0:
        raise ValueError("context token budget must be positive")

    selected = next(task for task in context.tasks if task.id == context.selected_task_id)
    candidates = [task for task in context.tasks if task.id != selected.id]
    focus_terms = _task_terms(selected) | _terms(planning_prompt)
    candidates.sort(
        key=lambda task: _task_order(task, selected=selected, focus_terms=focus_terms),
        reverse=True,
    )

    payload: dict[str, object] = {
        "project": context.project.model_dump(mode="json", exclude_none=True),
        "selected_task_id": selected.id,
        "selected_task": _full_task(selected),
        "related_tasks": [],
        "backlog_index": [],
        "omitted_task_count": len(candidates),
    }
    serialized = _serialize(payload)
    if estimate_tokens(serialized) > max_tokens:
        raise ValueError("context budget is too small to preserve the selected ticket")

    detailed: list[int] = []
    included: set[int] = set()
    related_tasks = payload["related_tasks"]
    assert isinstance(related_tasks, list)
    for task in candidates[:_MAX_DETAILED_TASKS]:
        related_tasks.append(_full_task(task))
        payload["omitted_task_count"] = len(candidates) - len(included) - 1
        candidate_json = _serialize(payload)
        if estimate_tokens(candidate_json) > max_tokens:
            related_tasks.pop()
            payload["omitted_task_count"] = len(candidates) - len(included)
            continue
        detailed.append(task.id)
        included.add(task.id)
        serialized = candidate_json

    indexed: list[int] = []
    backlog_index = payload["backlog_index"]
    assert isinstance(backlog_index, list)
    for task in candidates:
        if task.id in included:
            continue
        backlog_index.append(_compact_task(task))
        payload["omitted_task_count"] = len(candidates) - len(included) - len(indexed) - 1
        candidate_json = _serialize(payload)
        if estimate_tokens(candidate_json) > max_tokens:
            backlog_index.pop()
            payload["omitted_task_count"] = len(candidates) - len(included) - len(indexed)
            continue
        indexed.append(task.id)
        serialized = candidate_json

    omitted = len(candidates) - len(detailed) - len(indexed)
    payload["omitted_task_count"] = omitted
    serialized = _serialize(payload)
    return CompiledPlanningContext(
        context_json=serialized,
        estimated_tokens=estimate_tokens(serialized),
        detailed_task_ids=tuple(detailed),
        indexed_task_ids=tuple(indexed),
        omitted_task_count=omitted,
    )
