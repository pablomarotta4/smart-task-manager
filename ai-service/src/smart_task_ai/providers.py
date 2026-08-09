from __future__ import annotations

from typing import Protocol

from smart_task_ai.contracts import BriefAnalysis, ProjectDraft


class PlanningModel(Protocol):
    """Provider-neutral boundary used by the planning graph."""

    @property
    def model_name(self) -> str: ...

    async def generate(self, *, system_prompt: str, user_prompt: str) -> ProjectDraft: ...


class BriefAnalyzer(Protocol):
    """Provider-neutral boundary for extracting explicit brief capabilities."""

    async def analyze(self, *, system_prompt: str, user_prompt: str) -> BriefAnalysis: ...
