from __future__ import annotations

from typing import Self

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="SMART_TASK_AI_", extra="ignore")

    ollama_base_url: str = "http://127.0.0.1:11434"
    ollama_model: str = "llama3.2:3b"
    ollama_timeout_seconds: float = Field(default=60, gt=0, le=300)
    ollama_temperature: float = Field(default=0.2, ge=0, le=1)
    ollama_context_tokens: int = Field(default=8_192, ge=2_048, le=131_072)
    ollama_output_tokens: int = Field(default=2_048, ge=512, le=16_384)
    ollama_seed: int | None = Field(default=None, ge=0, le=2_147_483_647)
    planning_input_tokens: int = Field(default=5_000, ge=1_000, le=100_000)

    @model_validator(mode="after")
    def validate_token_budget(self) -> Self:
        if self.planning_input_tokens + self.ollama_output_tokens > self.ollama_context_tokens:
            raise ValueError("planning input and output budgets must fit the Ollama context window")
        return self
