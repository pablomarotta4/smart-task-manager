from __future__ import annotations

from dataclasses import dataclass
from uuid import UUID, uuid4

import httpx

from smart_task_ai.api import create_app
from smart_task_ai.contracts import (
    PlanningContext,
    PlanningResponse,
    Priority,
    ProjectDraft,
    QualityMetrics,
    QualityReport,
    TicketDraft,
)
from smart_task_ai.correlation import get_correlation_id
from smart_task_ai.ollama import OllamaPlanningModel, ProviderResponseError, ProviderTimeout
from smart_task_ai.providers import ProviderReadiness


def response_for(run_id: UUID) -> PlanningResponse:
    tickets = [
        TicketDraft(
            client_id=client_id,
            title=title,
            description=(
                f"Deliver {title.lower()} as a complete slice with validation, errors, and "
                "automated behavior coverage."
            ),
            priority=Priority.MEDIUM,
            estimated_hours=4,
            acceptance_criteria=[
                f"A user can complete {title.lower()}",
                f"Automated tests cover {title.lower()} failures",
            ],
        )
        for client_id, title in [
            ("accounts", "Create household accounts"),
            ("expenses", "Record categorized expenses"),
            ("summary", "Review monthly spending summary"),
        ]
    ]
    return PlanningResponse(
        run_id=run_id,
        draft=ProjectDraft(
            name="Budget App",
            objective="Help a household record expenses and understand its monthly spending.",
            tickets=tickets,
        ),
        quality=QualityReport(
            score=100,
            passed=True,
            issues=[],
            metrics=QualityMetrics(
                ticket_count=3,
                unique_title_ratio=1,
                max_title_similarity=0.4,
                description_coverage=1,
                acceptance_criteria_coverage=1,
            ),
        ),
        revision_count=0,
        model="fake-model",
    )


@dataclass
class FakePlanner:
    failure: Exception | None = None
    received_prompt: str | None = None
    received_context: PlanningContext | None = None

    async def plan(
        self,
        *,
        run_id: UUID,
        prompt: str,
        context: PlanningContext | None = None,
    ) -> PlanningResponse:
        self.received_prompt = prompt
        self.received_context = context
        if self.failure:
            raise self.failure
        return response_for(run_id)


@dataclass
class FakeReadinessProbe:
    result: ProviderReadiness
    calls: int = 0

    async def check_readiness(self) -> ProviderReadiness:
        self.calls += 1
        return self.result


@dataclass
class ProviderCallingPlanner:
    model: OllamaPlanningModel

    async def plan(
        self,
        *,
        run_id: UUID,
        prompt: str,
        context: PlanningContext | None = None,
    ) -> PlanningResponse:
        await self.model.generate(system_prompt="system", user_prompt=prompt)
        return response_for(run_id)


async def test_health_endpoint_is_ready_without_calling_model() -> None:
    app = create_app(planner=FakePlanner())
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


async def test_propagates_safe_correlation_id_and_clears_request_context() -> None:
    app = create_app(planner=FakePlanner())
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.get(
            "/health",
            headers={"X-Correlation-ID": "spring-request_2026.08:12"},
        )

    assert response.headers["X-Correlation-ID"] == "spring-request_2026.08:12"
    assert get_correlation_id() is None


async def test_replaces_unsafe_correlation_id_with_generated_uuid() -> None:
    app = create_app(planner=FakePlanner())
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.get(
            "/health",
            headers={"X-Correlation-ID": "token=secret value\nnot-safe"},
        )

    generated_id = response.headers["X-Correlation-ID"]
    assert UUID(generated_id).version == 4
    assert "secret" not in generated_id
    assert get_correlation_id() is None


async def test_unexpected_failure_preserves_safe_correlation_id_without_details() -> None:
    correlation_id = "spring-request-500"
    failure_detail = "private planner failure"
    app = create_app(planner=FakePlanner(failure=RuntimeError(failure_detail)))
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app, raise_app_exceptions=False),
        base_url="http://test",
    ) as client:
        response = await client.post(
            "/internal/v1/project-plans",
            headers={"X-Correlation-ID": correlation_id},
            json={
                "contract_version": "v1",
                "run_id": str(uuid4()),
                "prompt": "Build a useful household budget application",
            },
        )

    assert response.status_code == 500
    assert response.headers["X-Correlation-ID"] == correlation_id
    assert failure_detail not in response.text
    assert get_correlation_id() is None


async def test_unexpected_failure_replaces_unsafe_correlation_id() -> None:
    unsafe_correlation_id = "token=private planner failure"
    app = create_app(planner=FakePlanner(failure=RuntimeError("planner exploded")))
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app, raise_app_exceptions=False),
        base_url="http://test",
    ) as client:
        response = await client.post(
            "/internal/v1/project-plans",
            headers={"X-Correlation-ID": unsafe_correlation_id},
            json={
                "contract_version": "v1",
                "run_id": str(uuid4()),
                "prompt": "Build a useful household budget application",
            },
        )

    generated_id = response.headers["X-Correlation-ID"]
    assert response.status_code == 500
    assert UUID(generated_id).version == 4
    assert "private" not in generated_id
    assert get_correlation_id() is None


async def test_propagates_correlation_id_to_model_provider() -> None:
    correlation_id = "spring-request-2026-08-12"
    provider_draft = response_for(uuid4()).draft

    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.headers["X-Correlation-ID"] == correlation_id
        return httpx.Response(
            200,
            json={"message": {"content": provider_draft.model_dump_json()}},
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as provider_client:
        model = OllamaPlanningModel(
            base_url="http://ollama",
            model="test",
            client=provider_client,
        )
        app = create_app(planner=ProviderCallingPlanner(model))
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.post(
                "/internal/v1/project-plans",
                headers={"X-Correlation-ID": correlation_id},
                json={
                    "contract_version": "v1",
                    "run_id": str(uuid4()),
                    "prompt": "Build a useful household budget application",
                },
            )

    assert response.status_code == 200


async def test_ready_endpoint_checks_the_configured_model() -> None:
    probe = FakeReadinessProbe(ProviderReadiness(ready=True, model="gemma3:4b", reason="ready"))
    app = create_app(planner=FakePlanner(), readiness_probe=probe)
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.get("/ready")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ready",
        "model": "gemma3:4b",
        "reason": "ready",
    }
    assert probe.calls == 1


async def test_ready_endpoint_reports_a_missing_model_without_provider_details() -> None:
    probe = FakeReadinessProbe(
        ProviderReadiness(
            ready=False,
            model="llama3.2:3b",
            reason="configured_model_missing",
        )
    )
    app = create_app(planner=FakePlanner(), readiness_probe=probe)
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.get("/ready")

    assert response.status_code == 503
    assert response.json() == {
        "status": "not_ready",
        "model": "llama3.2:3b",
        "reason": "configured_model_missing",
    }
    assert "ollama" not in response.text.lower()


async def test_creates_versioned_project_plan() -> None:
    run_id = uuid4()
    planner = FakePlanner()
    app = create_app(planner=planner)
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.post(
            "/internal/v1/project-plans",
            json={
                "contract_version": "v1",
                "run_id": str(run_id),
                "prompt": "Build a useful household budget application",
            },
        )

    assert response.status_code == 200
    assert response.json()["run_id"] == str(run_id)
    assert response.json()["draft"]["name"] == "Budget App"
    assert response.json()["quality"]["passed"] is True
    assert planner.received_prompt == "Build a useful household budget application"


async def test_rejects_invalid_request_before_calling_planner() -> None:
    planner = FakePlanner()
    app = create_app(planner=planner)
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.post(
            "/internal/v1/project-plans",
            json={"contract_version": "v2", "run_id": str(uuid4()), "prompt": "tiny"},
        )

    assert response.status_code == 422
    assert planner.received_prompt is None


async def test_passes_existing_task_context_to_the_planner() -> None:
    planner = FakePlanner()
    app = create_app(planner=planner)
    payload: dict[str, object] = {
        "contract_version": "v1",
        "run_id": str(uuid4()),
        "prompt": "Break this ticket into implementation steps",
        "context": {
            "mode": "EXISTING_TASK",
            "project": {
                "id": 20,
                "name": "Budget App",
                "objective": "Help a household manage its monthly budget.",
            },
            "selected_task_id": 201,
            "tasks": [
                {
                    "id": 201,
                    "title": "Import bank transactions",
                    "description": "Import normalized transactions from a bank export.",
                    "status": "TODO",
                    "priority": "HIGH",
                    "position": 0,
                    "acceptance_criteria": [],
                    "depends_on_task_ids": [],
                }
            ],
        },
    }
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.post(
            "/internal/v1/project-plans",
            json=payload,
        )

    assert response.status_code == 200
    assert planner.received_context is not None
    assert planner.received_context.selected_task_id == 201


async def test_maps_provider_timeout_without_exposing_prompt() -> None:
    secret_prompt = "Build a private acquisition planning tool"
    app = create_app(planner=FakePlanner(failure=ProviderTimeout("provider was slow")))
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.post(
            "/internal/v1/project-plans",
            json={"contract_version": "v1", "run_id": str(uuid4()), "prompt": secret_prompt},
        )

    assert response.status_code == 504
    assert secret_prompt not in response.text


async def test_maps_invalid_provider_response_to_bad_gateway() -> None:
    app = create_app(planner=FakePlanner(failure=ProviderResponseError("bad response")))
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.post(
            "/internal/v1/project-plans",
            json={
                "contract_version": "v1",
                "run_id": str(uuid4()),
                "prompt": "Build a useful household budget application",
            },
        )

    assert response.status_code == 502
