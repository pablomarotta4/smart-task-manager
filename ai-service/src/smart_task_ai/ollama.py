from __future__ import annotations

import httpx

from smart_task_ai.contracts import ProjectDraft


class ProviderError(RuntimeError):
    """Base error for model-provider failures safe to map at the API boundary."""


class ProviderTimeout(ProviderError):
    """The model provider did not answer within the configured deadline."""


class ProviderResponseError(ProviderError):
    """The model provider failed or returned a draft outside the contract."""


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
        payload = {
            "model": self._model,
            "stream": False,
            "format": ProjectDraft.model_json_schema(),
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
            return ProjectDraft.model_validate_json(content)
        except httpx.TimeoutException as exc:
            raise ProviderTimeout("AI provider timed out") from exc
        except (httpx.HTTPError, KeyError, TypeError, ValueError) as exc:
            raise ProviderResponseError("AI provider returned an invalid response") from exc
        finally:
            if owns_client:
                await client.aclose()

