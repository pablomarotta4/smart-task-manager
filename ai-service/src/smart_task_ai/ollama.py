from __future__ import annotations

import logging
from collections import deque
from time import perf_counter
from typing import TypeVar, cast
from uuid import UUID

import httpx
from pydantic import BaseModel

from smart_task_ai.contracts import BriefAnalysis, ProjectDraft
from smart_task_ai.providers import (
    ModelCallMetadata,
    ModelCallMetric,
    ModelCallOutcome,
    ModelCallPhase,
    ProviderReadiness,
)

logger = logging.getLogger(__name__)

StructuredResponse = TypeVar("StructuredResponse", bound=BaseModel)

PROJECT_DRAFT_SCHEMA: dict[str, object] = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "name": {"type": "string"},
        "objective": {"type": "string"},
        "assumptions": {
            "type": "array",
            "items": {"type": "string"},
        },
        "risks": {
            "type": "array",
            "items": {"type": "string"},
        },
        "open_questions": {
            "type": "array",
            "items": {"type": "string"},
        },
        "tickets": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "client_id": {"type": "string"},
                    "title": {"type": "string"},
                    "description": {"type": "string"},
                    "priority": {
                        "type": "string",
                        "enum": ["LOW", "MEDIUM", "HIGH", "URGENT"],
                    },
                    "estimated_hours": {"type": "number"},
                    "acceptance_criteria": {
                        "type": "array",
                        "items": {"type": "string"},
                    },
                    "depends_on": {
                        "type": "array",
                        "items": {"type": "string"},
                    },
                    "category": {"type": ["string", "null"]},
                    "due_in_days": {"type": ["integer", "null"]},
                },
                "required": [
                    "client_id",
                    "title",
                    "description",
                    "priority",
                    "estimated_hours",
                    "acceptance_criteria",
                ],
            },
        },
    },
    "required": ["name", "objective", "tickets"],
}

BRIEF_ANALYSIS_SCHEMA: dict[str, object] = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "explicit_capabilities": {
            "type": "array",
            "items": {"type": "string"},
        }
    },
    "required": ["explicit_capabilities"],
}


class ProviderError(RuntimeError):
    """Base error for model-provider failures safe to map at the API boundary."""


class ProviderTimeout(ProviderError):
    """The model provider did not answer within the configured deadline."""


class ProviderResponseError(ProviderError):
    """The model provider failed or returned a draft outside the contract."""


class ProviderMalformedResponse(ProviderResponseError):
    """The provider answered successfully but its structured content was invalid."""


class OllamaPlanningModel:
    def __init__(
        self,
        *,
        base_url: str,
        model: str,
        timeout_seconds: float = 60,
        temperature: float = 0.2,
        context_tokens: int = 8_192,
        output_tokens: int = 2_048,
        seed: int | None = None,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._model = model
        self._timeout = httpx.Timeout(timeout_seconds)
        self._temperature = temperature
        self._context_tokens = context_tokens
        self._output_tokens = output_tokens
        self._seed = seed
        self._client = client
        self._metrics: deque[ModelCallMetric] = deque(maxlen=10_000)

    @property
    def model_name(self) -> str:
        return self._model

    def metrics_for_run(self, run_id: UUID) -> list[ModelCallMetric]:
        return [metric for metric in self._metrics if metric.run_id == run_id]

    @staticmethod
    def _metadata(
        metadata: ModelCallMetadata | None,
        *,
        default_phase: ModelCallPhase,
    ) -> ModelCallMetadata:
        return metadata or ModelCallMetadata(run_id=UUID(int=0), phase=default_phase)

    async def generate(
        self,
        *,
        system_prompt: str,
        user_prompt: str,
        metadata: ModelCallMetadata | None = None,
    ) -> ProjectDraft:
        return await self._request_with_contract_retry(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            schema=PROJECT_DRAFT_SCHEMA,
            response_type=ProjectDraft,
            metadata=self._metadata(metadata, default_phase="generation"),
        )

    async def analyze(
        self,
        *,
        system_prompt: str,
        user_prompt: str,
        metadata: ModelCallMetadata | None = None,
    ) -> BriefAnalysis:
        return await self._request_with_contract_retry(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            schema=BRIEF_ANALYSIS_SCHEMA,
            response_type=BriefAnalysis,
            metadata=self._metadata(metadata, default_phase="brief_analysis"),
        )

    async def _request_with_contract_retry(
        self,
        *,
        system_prompt: str,
        user_prompt: str,
        schema: dict[str, object],
        response_type: type[StructuredResponse],
        metadata: ModelCallMetadata,
    ) -> StructuredResponse:
        try:
            return await self._request(
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                schema=schema,
                response_type=response_type,
                metadata=metadata,
                attempt=1,
            )
        except ProviderMalformedResponse:
            repair_instruction = (
                f"{system_prompt}\nYour previous answer was incomplete or outside the contract. "
                "Return one complete JSON object and no surrounding prose."
            )
            return await self._request(
                system_prompt=repair_instruction,
                user_prompt=user_prompt,
                schema=schema,
                response_type=response_type,
                metadata=metadata,
                attempt=2,
            )

    async def _request(
        self,
        *,
        system_prompt: str,
        user_prompt: str,
        schema: dict[str, object],
        response_type: type[StructuredResponse],
        metadata: ModelCallMetadata,
        attempt: int,
    ) -> StructuredResponse:
        options: dict[str, float | int] = {
            "temperature": self._temperature,
            "num_ctx": self._context_tokens,
            "num_predict": self._output_tokens,
        }
        if self._seed is not None:
            options["seed"] = self._seed
        payload = {
            "model": self._model,
            "stream": False,
            # Keep the provider grammar structural. Nested length and collection
            # bounds exceed Ollama's grammar complexity limit for this contract;
            # Pydantic applies the complete constraints to the returned JSON below.
            "format": schema,
            "options": options,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
        }
        owns_client = self._client is None
        client = self._client or httpx.AsyncClient(timeout=self._timeout)
        started_at = perf_counter()
        prompt_tokens = 0
        output_tokens = 0
        provider_duration_ms: float | None = None
        outcome: ModelCallOutcome = "provider_error"
        try:
            response = await client.post(f"{self._base_url}/api/chat", json=payload)
            response.raise_for_status()
            body = cast(dict[str, object], response.json())
            prompt_tokens = self._non_negative_int(body.get("prompt_eval_count"))
            output_tokens = self._non_negative_int(body.get("eval_count"))
            total_duration = body.get("total_duration")
            if isinstance(total_duration, int | float) and total_duration >= 0:
                provider_duration_ms = float(total_duration) / 1_000_000
            raw_message = body.get("message")
            if not isinstance(raw_message, dict):
                raise TypeError("message is not an object")
            message = cast(dict[str, object], raw_message)
            content = message.get("content")
            if not isinstance(content, str):
                raise TypeError("message content is not text")
            result = response_type.model_validate_json(content)
            outcome = "success"
            return result
        except httpx.TimeoutException as exc:
            outcome = "timeout"
            raise ProviderTimeout("AI provider timed out") from exc
        except httpx.HTTPError as exc:
            outcome = "provider_error"
            raise ProviderResponseError("AI provider returned an invalid response") from exc
        except (KeyError, TypeError, ValueError) as exc:
            outcome = "malformed"
            raise ProviderMalformedResponse(
                "AI provider returned invalid structured content"
            ) from exc
        finally:
            duration_ms = provider_duration_ms or (perf_counter() - started_at) * 1_000
            metric = ModelCallMetric(
                run_id=metadata.run_id,
                model=self._model,
                phase=metadata.phase,
                attempt=attempt,
                prompt_tokens=prompt_tokens,
                output_tokens=output_tokens,
                duration_ms=duration_ms,
                outcome=outcome,
            )
            self._metrics.append(metric)
            logger.info(
                "AI model call model=%s run_id=%s phase=%s attempt=%d "
                "prompt_tokens=%d output_tokens=%d duration_ms=%.1f outcome=%s",
                metric.model,
                metric.run_id,
                metric.phase,
                metric.attempt,
                metric.prompt_tokens,
                metric.output_tokens,
                metric.duration_ms,
                metric.outcome,
            )
            if owns_client:
                await client.aclose()

    @staticmethod
    def _non_negative_int(value: object) -> int:
        return value if isinstance(value, int) and value >= 0 else 0

    async def check_readiness(self) -> ProviderReadiness:
        owns_client = self._client is None
        client = self._client or httpx.AsyncClient(timeout=self._timeout)
        try:
            response = await client.get(f"{self._base_url}/api/tags")
            response.raise_for_status()
            body = cast(dict[str, object], response.json())
            raw_models = body.get("models")
            if not isinstance(raw_models, list):
                raise TypeError("models is not a list")
            models = cast(list[object], raw_models)
            installed_names: set[str] = set()
            for raw_item in models:
                if not isinstance(raw_item, dict):
                    continue
                item = cast(dict[str, object], raw_item)
                for name in (item.get("name"), item.get("model")):
                    if isinstance(name, str):
                        installed_names.add(name)
            if self._model in installed_names:
                return ProviderReadiness(ready=True, model=self._model, reason="ready")
            return ProviderReadiness(
                ready=False,
                model=self._model,
                reason="configured_model_missing",
            )
        except httpx.HTTPError:
            return ProviderReadiness(
                ready=False,
                model=self._model,
                reason="provider_unreachable",
            )
        except (KeyError, TypeError, ValueError):
            return ProviderReadiness(
                ready=False,
                model=self._model,
                reason="invalid_provider_response",
            )
        finally:
            if owns_client:
                await client.aclose()
