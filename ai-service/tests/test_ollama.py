from __future__ import annotations

import json

import httpx
import pytest

from smart_task_ai.contracts import Priority, ProjectDraft, TicketDraft
from smart_task_ai.ollama import OllamaPlanningModel, ProviderResponseError, ProviderTimeout


def good_draft() -> ProjectDraft:
    tickets = [
        TicketDraft(
            client_id=client_id,
            title=title,
            description=(
                f"Deliver {title.lower()} as a complete user-facing slice with validation, "
                "error handling, and automated test coverage."
            ),
            priority=Priority.MEDIUM,
            estimated_hours=4,
            acceptance_criteria=[
                f"A user can complete {title.lower()}",
                f"Failures in {title.lower()} have automated coverage",
            ],
        )
        for client_id, title in [
            ("accounts", "Create household accounts"),
            ("expenses", "Record categorized expenses"),
            ("budgets", "Configure monthly budgets"),
        ]
    ]
    return ProjectDraft(
        name="Budget App",
        objective="Help a household record expenses and understand its available monthly budget.",
        assumptions=["The first version supports one household"],
        risks=["Users may enter incomplete expense data"],
        tickets=tickets,
    )


async def test_sends_schema_constrained_chat_request_and_parses_draft() -> None:
    draft = good_draft()

    async def handler(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.content)
        assert request.url == "http://ollama:11434/api/chat"
        assert payload["model"] == "llama-test"
        assert payload["stream"] is False
        assert payload["format"]["type"] == "object"
        assert payload["options"] == {"temperature": 0.2}
        assert payload["messages"][0]["role"] == "system"
        assert payload["messages"][1]["role"] == "user"
        return httpx.Response(200, json={"message": {"content": draft.model_dump_json()}})

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        model = OllamaPlanningModel(
            base_url="http://ollama:11434", model="llama-test", timeout_seconds=5, client=client
        )
        result = await model.generate(system_prompt="system", user_prompt="user")

    assert result == draft
    assert model.model_name == "llama-test"


async def test_maps_http_timeout_to_provider_timeout() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("slow model", request=request)

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        model = OllamaPlanningModel(base_url="http://ollama", model="test", client=client)
        with pytest.raises(ProviderTimeout):
            await model.generate(system_prompt="system", user_prompt="user")


@pytest.mark.parametrize(
    "response",
    [
        httpx.Response(503, text="offline"),
        httpx.Response(200, json={"message": {"content": "not-json"}}),
        httpx.Response(200, json={"unexpected": True}),
    ],
)
async def test_rejects_upstream_and_malformed_responses(response: httpx.Response) -> None:
    async def handler(_: httpx.Request) -> httpx.Response:
        return response

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        model = OllamaPlanningModel(base_url="http://ollama", model="test", client=client)
        with pytest.raises(ProviderResponseError):
            await model.generate(system_prompt="system", user_prompt="user")

