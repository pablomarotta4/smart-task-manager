from __future__ import annotations

from typing import TypeVar

import httpx
from pydantic import BaseModel

from smart_task_ai.contracts import BriefAnalysis, ProjectDraft

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
        client: httpx.AsyncClient | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._model = model
        self._timeout = httpx.Timeout(timeout_seconds)
        self._temperature = temperature
        self._client = client

    @property
    def model_name(self) -> str:
        return self._model

    async def generate(self, *, system_prompt: str, user_prompt: str) -> ProjectDraft:
        return await self._request_with_contract_retry(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            schema=PROJECT_DRAFT_SCHEMA,
            response_type=ProjectDraft,
        )

    async def analyze(self, *, system_prompt: str, user_prompt: str) -> BriefAnalysis:
        return await self._request_with_contract_retry(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            schema=BRIEF_ANALYSIS_SCHEMA,
            response_type=BriefAnalysis,
        )

    async def _request_with_contract_retry(
        self,
        *,
        system_prompt: str,
        user_prompt: str,
        schema: dict[str, object],
        response_type: type[StructuredResponse],
    ) -> StructuredResponse:
        try:
            return await self._request(
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                schema=schema,
                response_type=response_type,
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
            )

    async def _request(
        self,
        *,
        system_prompt: str,
        user_prompt: str,
        schema: dict[str, object],
        response_type: type[StructuredResponse],
    ) -> StructuredResponse:
        payload = {
            "model": self._model,
            "stream": False,
            # Keep the provider grammar structural. Nested length and collection
            # bounds exceed Ollama's grammar complexity limit for this contract;
            # Pydantic applies the complete constraints to the returned JSON below.
            "format": schema,
            "options": {"temperature": self._temperature},
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
        }
        owns_client = self._client is None
        client = self._client or httpx.AsyncClient(timeout=self._timeout)
        try:
            response = await client.post(f"{self._base_url}/api/chat", json=payload)
            response.raise_for_status()
            body = response.json()
            content = body["message"]["content"]
            if not isinstance(content, str):
                raise TypeError("message content is not text")
            return response_type.model_validate_json(content)
        except httpx.TimeoutException as exc:
            raise ProviderTimeout("AI provider timed out") from exc
        except httpx.HTTPError as exc:
            raise ProviderResponseError("AI provider returned an invalid response") from exc
        except (KeyError, TypeError, ValueError) as exc:
            raise ProviderMalformedResponse(
                "AI provider returned invalid structured content"
            ) from exc
        finally:
            if owns_client:
                await client.aclose()
