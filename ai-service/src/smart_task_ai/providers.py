from __future__ import annotations

from dataclasses import dataclass
from typing import Literal, Protocol
from uuid import UUID

from smart_task_ai.contracts import BriefAnalysis, ProjectDraft

ModelCallPhase = Literal["brief_analysis", "generation", "revision"]
ModelCallOutcome = Literal["success", "malformed", "timeout", "provider_error"]
ReadinessReason = Literal[
    "ready",
    "configured_model_missing",
    "provider_unreachable",
    "invalid_provider_response",
    "readiness_probe_unavailable",
]


@dataclass(frozen=True, slots=True)
class ModelCallMetadata:
    run_id: UUID
    phase: ModelCallPhase


@dataclass(frozen=True, slots=True)
class ModelCallMetric:
    run_id: UUID
    model: str
    phase: ModelCallPhase
    attempt: int
    prompt_tokens: int
    output_tokens: int
    duration_ms: float
    outcome: ModelCallOutcome


@dataclass(frozen=True, slots=True)
class ProviderReadiness:
    ready: bool
    model: str
    reason: ReadinessReason


class ReadinessProbe(Protocol):
    async def check_readiness(self) -> ProviderReadiness: ...


class ModelMetricsReader(Protocol):
    def metrics_for_run(self, run_id: UUID) -> list[ModelCallMetric]: ...


class PlanningModel(Protocol):
    """Provider-neutral boundary used by the planning graph."""

    @property
    def model_name(self) -> str: ...

    async def generate(
        self,
        *,
        system_prompt: str,
        user_prompt: str,
        metadata: ModelCallMetadata | None = None,
    ) -> ProjectDraft: ...


class BriefAnalyzer(Protocol):
    """Provider-neutral boundary for extracting explicit brief capabilities."""

    async def analyze(
        self,
        *,
        system_prompt: str,
        user_prompt: str,
        metadata: ModelCallMetadata | None = None,
    ) -> BriefAnalysis: ...
