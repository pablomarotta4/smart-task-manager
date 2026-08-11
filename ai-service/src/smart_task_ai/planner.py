from __future__ import annotations

from typing import Literal, TypedDict, cast
from uuid import UUID

from langgraph.graph import END, START, StateGraph

from smart_task_ai.contracts import (
    PlanningContext,
    PlanningResponse,
    ProjectDraft,
    QualityIssue,
    QualityReport,
)
from smart_task_ai.prompts import (
    BRIEF_ANALYSIS_SYSTEM_PROMPT,
    PLANNER_SYSTEM_PROMPT,
    TASK_PLANNER_SYSTEM_PROMPT,
    brief_analysis_prompt,
    generation_prompt,
    revision_prompt,
)
from smart_task_ai.providers import BriefAnalyzer, ModelCallMetadata, PlanningModel
from smart_task_ai.quality import evaluate_draft, find_missing_capabilities


class PlanningState(TypedDict, total=False):
    prompt: str
    draft: ProjectDraft
    quality: QualityReport
    revision_count: int
    explicit_capabilities: list[str]
    context: PlanningContext | None
    run_id: UUID


class ProjectPlanner:
    """Bounded project-planning graph with brief analysis and structural review."""

    def __init__(
        self,
        model: PlanningModel,
        *,
        brief_analyzer: BriefAnalyzer | None = None,
        max_input_tokens: int = 5_000,
    ) -> None:
        self._model = model
        self._brief_analyzer = brief_analyzer
        self._max_input_tokens = max_input_tokens
        graph = StateGraph(PlanningState)
        if brief_analyzer is not None:
            graph.add_node("analyze", self._analyze)
        graph.add_node("generate", self._generate)
        graph.add_node("assess", self._assess)
        graph.add_node("revise", self._revise)
        graph.add_node("finalize", self._finalize)
        if brief_analyzer is not None:
            graph.add_edge(START, "analyze")
            graph.add_edge("analyze", "generate")
        else:
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

    async def _analyze(self, state: PlanningState) -> PlanningState:
        prompt = state.get("prompt")
        run_id = state.get("run_id")
        if prompt is None or run_id is None or self._brief_analyzer is None:
            raise RuntimeError("planning graph cannot analyze a missing brief")
        context = state.get("context")
        if context is not None:
            selected = next(task for task in context.tasks if task.id == context.selected_task_id)
            capabilities = selected.acceptance_criteria or [selected.title]
            return PlanningState(explicit_capabilities=capabilities)
        analysis = await self._brief_analyzer.analyze(
            system_prompt=BRIEF_ANALYSIS_SYSTEM_PROMPT,
            user_prompt=brief_analysis_prompt(prompt),
            metadata=ModelCallMetadata(run_id=run_id, phase="brief_analysis"),
        )
        return PlanningState(explicit_capabilities=analysis.explicit_capabilities)

    async def plan(
        self,
        *,
        run_id: UUID,
        prompt: str,
        context: PlanningContext | None = None,
    ) -> PlanningResponse:
        final_state = cast(
            PlanningState,
            await self._graph.ainvoke(
                PlanningState(
                    prompt=prompt,
                    revision_count=0,
                    context=context,
                    run_id=run_id,
                )
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
        run_id = state.get("run_id")
        if prompt is None or run_id is None:
            raise RuntimeError("planning graph requires a prompt")
        context = state.get("context")
        system_prompt = TASK_PLANNER_SYSTEM_PROMPT if context is not None else PLANNER_SYSTEM_PROMPT
        draft = await self._model.generate(
            system_prompt=system_prompt,
            user_prompt=generation_prompt(
                prompt,
                state.get("explicit_capabilities"),
                context,
                max_input_tokens=self._max_input_tokens,
            ),
            metadata=ModelCallMetadata(run_id=run_id, phase="generation"),
        )
        return PlanningState(draft=draft)

    @staticmethod
    def _assess(state: PlanningState) -> PlanningState:
        draft = state.get("draft")
        if draft is None:
            raise RuntimeError("planning graph cannot assess a missing draft")
        context = state.get("context")
        quality = evaluate_draft(draft, context=context)
        if context is not None:
            return PlanningState(quality=quality)
        missing_capabilities = find_missing_capabilities(
            draft, state.get("explicit_capabilities", [])
        )
        if not missing_capabilities:
            return PlanningState(quality=quality)

        preview = "; ".join(missing_capabilities[:4])
        if len(missing_capabilities) > 4:
            preview = f"{preview}; and {len(missing_capabilities) - 4} more"
        issue = QualityIssue(
            code="missing_explicit_capabilities",
            message=f"Tickets must explicitly implement these brief capabilities: {preview}",
        )
        return PlanningState(
            quality=quality.model_copy(
                update={
                    "score": max(0, quality.score - 35),
                    "passed": False,
                    "issues": [*quality.issues, issue],
                }
            )
        )

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
        run_id = state.get("run_id")
        if prompt is None or draft is None or quality is None or run_id is None:
            raise RuntimeError("planning graph cannot revise incomplete state")
        draft = await self._model.generate(
            system_prompt=(
                TASK_PLANNER_SYSTEM_PROMPT
                if state.get("context") is not None
                else PLANNER_SYSTEM_PROMPT
            ),
            user_prompt=revision_prompt(
                prompt,
                draft,
                quality,
                state.get("explicit_capabilities"),
                state.get("context"),
                max_input_tokens=self._max_input_tokens,
            ),
            metadata=ModelCallMetadata(run_id=run_id, phase="revision"),
        )
        return PlanningState(draft=draft, revision_count=state.get("revision_count", 0) + 1)

    @staticmethod
    def _finalize(state: PlanningState) -> PlanningState:
        return PlanningState()
