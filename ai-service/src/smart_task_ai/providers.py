from __future__ import annotations

from typing import Protocol

from smart_task_ai.contracts import ProjectDraft


class PlanningModel(Protocol):
    """Provider-neutral boundary used by the planning graph."""

    @property
    def model_name(self) -> str: ...

    async def generate(self, *, system_prompt: str, user_prompt: str) -> ProjectDraft: ...

