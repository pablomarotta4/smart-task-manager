from __future__ import annotations

import logging
from typing import Protocol
from uuid import UUID

from fastapi import APIRouter, FastAPI, HTTPException, status

from smart_task_ai.contracts import PlanningRequest, PlanningResponse
from smart_task_ai.ollama import (
    OllamaPlanningModel,
    ProviderResponseError,
    ProviderTimeout,
)
from smart_task_ai.planner import ProjectPlanner
from smart_task_ai.settings import Settings

logger = logging.getLogger(__name__)


class PlanningService(Protocol):
    async def plan(self, *, run_id: UUID, prompt: str) -> PlanningResponse: ...


def create_app(*, planner: PlanningService | None = None) -> FastAPI:
    if planner is None:
        settings = Settings()
        model = OllamaPlanningModel(
            base_url=settings.ollama_base_url,
            model=settings.ollama_model,
            timeout_seconds=settings.ollama_timeout_seconds,
            temperature=settings.ollama_temperature,
        )
        planner = ProjectPlanner(model)

    app = FastAPI(title="Smart Task AI", version="1.0.0")
    router = APIRouter()

    @router.get("/health")
    async def health() -> dict[str, str]:  # pyright: ignore[reportUnusedFunction]
        return {"status": "ok"}

    @router.post(
        "/internal/v1/project-plans",
        response_model=PlanningResponse,
        status_code=status.HTTP_200_OK,
    )
    async def create_project_plan(  # pyright: ignore[reportUnusedFunction]
        request: PlanningRequest,
    ) -> PlanningResponse:
        try:
            return await planner.plan(run_id=request.run_id, prompt=request.prompt)
        except ProviderTimeout as exc:
            logger.warning("AI planning timed out for run_id=%s", request.run_id)
            raise HTTPException(
                status_code=status.HTTP_504_GATEWAY_TIMEOUT,
                detail="AI provider timed out",
            ) from exc
        except ProviderResponseError as exc:
            logger.warning("AI planning provider failed for run_id=%s", request.run_id)
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="AI provider returned an invalid response",
            ) from exc

    app.include_router(router)
    return app
