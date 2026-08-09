from __future__ import annotations

from typing import Literal, TypedDict, cast
from uuid import UUID

from langgraph.graph import END, START, StateGraph

from smart_task_ai.contracts import PlanningResponse, ProjectDraft, QualityReport
from smart_task_ai.prompts import PLANNER_SYSTEM_PROMPT, generation_prompt, revision_prompt
from smart_task_ai.providers import PlanningModel
from smart_task_ai.quality import evaluate_draft


class PlanningState(TypedDict, total=False):
    prompt: str
    draft: ProjectDraft
    quality: QualityReport
    revision_count: int


class ProjectPlanner:
    """Bounded project-planning graph with deterministic output review."""

    def __init__(self, model: PlanningModel) -> None:
        self._model = model
        graph = StateGraph(PlanningState)
        graph.add_node("generate", self._generate)
        graph.add_node("assess", self._assess)
        graph.add_node("revise", self._revise)
        graph.add_node("finalize", self._finalize)
        graph.add_edge(START, "generate")
        graph.add_edge("generate", "assess")
        graph.add_conditional_edges(
            "assess",
            self._next_after_assessment,
            {"revise": "revise", "finalize": "finalize"},
        )
        graph.add_edge("revise", "assess")
        graph.add_edge("finalize", END)
        self._graph = graph.compile()

    async def plan(self, *, run_id: UUID, prompt: str) -> PlanningResponse:
        final_state = cast(
            PlanningState,
            await self._graph.ainvoke(
                PlanningState(prompt=prompt, revision_count=0)
            ),
        )
        draft = final_state.get("draft")
        quality = final_state.get("quality")
        if draft is None or quality is None:
            raise RuntimeError("planning graph finished without a draft and quality report")
        return PlanningResponse(
            run_id=run_id,
            draft=draft,
            quality=quality,
            revision_count=final_state.get("revision_count", 0),
            model=self._model.model_name,
        )

    async def _generate(self, state: PlanningState) -> PlanningState:
        prompt = state.get("prompt")
        if prompt is None:
            raise RuntimeError("planning graph requires a prompt")
        draft = await self._model.generate(
            system_prompt=PLANNER_SYSTEM_PROMPT,
            user_prompt=generation_prompt(prompt),
        )
        return PlanningState(draft=draft)

    @staticmethod
    def _assess(state: PlanningState) -> PlanningState:
        draft = state.get("draft")
        if draft is None:
            raise RuntimeError("planning graph cannot assess a missing draft")
        return PlanningState(quality=evaluate_draft(draft))

    @staticmethod
    def _next_after_assessment(state: PlanningState) -> Literal["revise", "finalize"]:
        quality = state.get("quality")
        if quality is None:
            raise RuntimeError("planning graph cannot route without a quality report")
        if not quality.passed and state.get("revision_count", 0) < 1:
            return "revise"
        return "finalize"

    async def _revise(self, state: PlanningState) -> PlanningState:
        prompt = state.get("prompt")
        draft = state.get("draft")
        quality = state.get("quality")
        if prompt is None or draft is None or quality is None:
            raise RuntimeError("planning graph cannot revise incomplete state")
        draft = await self._model.generate(
            system_prompt=PLANNER_SYSTEM_PROMPT,
            user_prompt=revision_prompt(prompt, draft, quality),
        )
        return PlanningState(draft=draft, revision_count=state.get("revision_count", 0) + 1)

    @staticmethod
    def _finalize(state: PlanningState) -> PlanningState:
        return PlanningState()
