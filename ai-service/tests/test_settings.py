from __future__ import annotations

import pytest
from pydantic import ValidationError

from smart_task_ai.settings import Settings


def test_reads_explicit_provider_and_planning_limits(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("SMART_TASK_AI_OLLAMA_CONTEXT_TOKENS", "16384")
    monkeypatch.setenv("SMART_TASK_AI_OLLAMA_OUTPUT_TOKENS", "3072")
    monkeypatch.setenv("SMART_TASK_AI_OLLAMA_SEED", "42")
    monkeypatch.setenv("SMART_TASK_AI_PLANNING_INPUT_TOKENS", "9000")

    settings = Settings()

    assert settings.ollama_context_tokens == 16_384
    assert settings.ollama_output_tokens == 3_072
    assert settings.ollama_seed == 42
    assert settings.planning_input_tokens == 9_000


def test_rejects_input_and_output_budgets_larger_than_context_window() -> None:
    with pytest.raises(ValidationError, match="must fit the Ollama context window"):
        Settings(
            ollama_context_tokens=4_096,
            ollama_output_tokens=2_048,
            planning_input_tokens=3_000,
        )
