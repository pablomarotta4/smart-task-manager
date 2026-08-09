from __future__ import annotations

import argparse
import asyncio
import json
from pathlib import Path
from typing import cast

from smart_task_ai.evaluation import EvaluationCase, evaluate_cases
from smart_task_ai.ollama import OllamaPlanningModel
from smart_task_ai.planner import ProjectPlanner
from smart_task_ai.settings import Settings


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate live Ollama project-planning behavior")
    parser.add_argument(
        "--cases",
        type=Path,
        default=Path(__file__).parents[1] / "evals" / "cases.json",
    )
    return parser.parse_args()


async def run() -> int:
    args = parse_args()
    raw_cases = cast(list[object], json.loads(args.cases.read_text()))
    cases = [EvaluationCase.model_validate(case) for case in raw_cases]
    settings = Settings()
    planner = ProjectPlanner(
        OllamaPlanningModel(
            base_url=settings.ollama_base_url,
            model=settings.ollama_model,
            timeout_seconds=settings.ollama_timeout_seconds,
            temperature=settings.ollama_temperature,
        )
    )
    summary = await evaluate_cases(cases, planner)
    print(summary.model_dump_json(indent=2))
    return 0 if summary.passed_cases == summary.total_cases else 1


if __name__ == "__main__":
    raise SystemExit(asyncio.run(run()))
