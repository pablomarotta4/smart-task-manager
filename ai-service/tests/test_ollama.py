from __future__ import annotations

import json
import logging
from uuid import uuid4

import httpx
import pytest

from smart_task_ai.contracts import BriefAnalysis, Priority, ProjectDraft, TicketDraft
from smart_task_ai.ollama import OllamaPlanningModel, ProviderResponseError, ProviderTimeout
from smart_task_ai.providers import ModelCallMetadata


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
        assert "$defs" not in payload["format"]
        assert payload["format"]["properties"]["tickets"]["items"]["type"] == "object"
        grammar_schema = json.dumps(payload["format"])
        for unsupported_bound in ("minLength", "maxLength", "minItems", "maxItems", "pattern"):
            assert unsupported_bound not in grammar_schema
        assert payload["options"] == {
            "temperature": 0.2,
            "num_ctx": 8_192,
            "num_predict": 2_048,
            "seed": 17,
        }
        assert "open_questions" in payload["format"]["properties"]
        assert payload["messages"][0]["role"] == "system"
        assert payload["messages"][1]["role"] == "user"
        return httpx.Response(200, json={"message": {"content": draft.model_dump_json()}})

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        model = OllamaPlanningModel(
            base_url="http://ollama:11434",
            model="llama-test",
            timeout_seconds=5,
            context_tokens=8_192,
            output_tokens=2_048,
            seed=17,
            client=client,
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


async def test_requests_and_parses_brief_analysis() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.content)
        assert payload["format"]["required"] == ["explicit_capabilities"]
        assert payload["messages"][0] == {"role": "system", "content": "analysis system"}
        return httpx.Response(
            200,
            json={"message": {"content": '{"explicit_capabilities":["return borrowed tools"]}'}},
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        model = OllamaPlanningModel(base_url="http://ollama", model="test", client=client)
        result = await model.analyze(system_prompt="analysis system", user_prompt="analysis user")

    assert result == BriefAnalysis(explicit_capabilities=["return borrowed tools"])


async def test_retries_one_incomplete_structured_generation() -> None:
    draft = good_draft()
    call_count = 0

    async def handler(_: httpx.Request) -> httpx.Response:
        nonlocal call_count
        call_count += 1
        content = '{"name":"truncated' if call_count == 1 else draft.model_dump_json()
        return httpx.Response(200, json={"message": {"content": content}})

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        model = OllamaPlanningModel(base_url="http://ollama", model="test", client=client)
        result = await model.generate(system_prompt="system", user_prompt="user")

    assert result == draft
    assert call_count == 2


async def test_contract_retry_names_invalid_fields_without_echoing_values() -> None:
    draft = good_draft()
    invalid = draft.model_copy(deep=True)
    invalid.tickets[0].client_id = "PRIVATE-UPPERCASE-ID"
    call_count = 0

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal call_count
        call_count += 1
        payload = json.loads(request.content)
        if call_count == 1:
            return httpx.Response(
                200,
                json={"message": {"content": invalid.model_dump_json()}},
            )
        repair_prompt = payload["messages"][0]["content"]
        assert "tickets.0.client_id (string_pattern_mismatch)" in repair_prompt
        assert "Convert every client_id to lowercase" in repair_prompt
        assert "under 50 characters" in repair_prompt
        assert "no more than five tickets" in repair_prompt
        assert "at least two non-empty acceptance criteria" in repair_prompt
        assert "A ticket must never depend on itself" in repair_prompt
        assert "must be acyclic" in repair_prompt
        assert "PRIVATE-UPPERCASE-ID" not in repair_prompt
        return httpx.Response(200, json={"message": {"content": draft.model_dump_json()}})

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        model = OllamaPlanningModel(base_url="http://ollama", model="test", client=client)
        result = await model.generate(system_prompt="system", user_prompt="user")

    assert result == draft
    assert call_count == 2


async def test_contract_repair_can_fix_progressive_validation_errors() -> None:
    draft = good_draft()
    invalid_id = draft.model_copy(deep=True)
    invalid_id.tickets[0].client_id = "PRIVATE-UPPERCASE-ID"
    empty_criteria = draft.model_copy(deep=True)
    empty_criteria.tickets[0].acceptance_criteria = []
    call_count = 0

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal call_count
        call_count += 1
        repair_prompt = json.loads(request.content)["messages"][0]["content"]
        if call_count == 1:
            return httpx.Response(
                200,
                json={"message": {"content": invalid_id.model_dump_json()}},
            )
        if call_count == 2:
            assert "tickets.0.client_id (string_pattern_mismatch)" in repair_prompt
            return httpx.Response(
                200,
                json={"message": {"content": empty_criteria.model_dump_json()}},
            )
        assert "tickets.0.acceptance_criteria (too_short)" in repair_prompt
        return httpx.Response(200, json={"message": {"content": draft.model_dump_json()}})

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        model = OllamaPlanningModel(base_url="http://ollama", model="test", client=client)
        result = await model.generate(system_prompt="system", user_prompt="user")

    assert result == draft
    assert call_count == 3


async def test_records_safe_per_attempt_metrics_without_prompt_content(caplog) -> None:  # type: ignore[no-untyped-def]
    draft = good_draft()
    run_id = uuid4()
    secret = "confidential launch plan"
    call_count = 0

    async def handler(_: httpx.Request) -> httpx.Response:
        nonlocal call_count
        call_count += 1
        content = "not-json" if call_count == 1 else draft.model_dump_json()
        return httpx.Response(
            200,
            json={
                "message": {"content": content},
                "prompt_eval_count": 321,
                "eval_count": 87,
                "total_duration": 1_500_000_000,
            },
        )

    caplog.set_level(logging.INFO, logger="smart_task_ai.ollama")
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        model = OllamaPlanningModel(base_url="http://ollama", model="test", client=client)
        result = await model.generate(
            system_prompt="system",
            user_prompt=secret,
            metadata=ModelCallMetadata(run_id=run_id, phase="generation"),
        )

    metrics = model.metrics_for_run(run_id)
    assert result == draft
    assert [metric.attempt for metric in metrics] == [1, 2]
    assert [metric.outcome for metric in metrics] == ["malformed", "success"]
    assert all(metric.prompt_tokens == 321 for metric in metrics)
    assert all(metric.output_tokens == 87 for metric in metrics)
    assert all(metric.duration_ms == 1_500 for metric in metrics)
    assert secret not in caplog.text
    assert str(run_id) in caplog.text


async def test_readiness_requires_the_configured_model_to_be_installed() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.url == "http://ollama/api/tags"
        return httpx.Response(
            200,
            json={"models": [{"name": "gemma3:4b"}, {"name": "deepseek-r1:8b"}]},
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        installed = OllamaPlanningModel(base_url="http://ollama", model="gemma3:4b", client=client)
        missing = OllamaPlanningModel(base_url="http://ollama", model="llama3.2:3b", client=client)

        installed_result = await installed.check_readiness()
        missing_result = await missing.check_readiness()

    assert installed_result.ready is True
    assert installed_result.reason == "ready"
    assert missing_result.ready is False
    assert missing_result.reason == "configured_model_missing"


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
