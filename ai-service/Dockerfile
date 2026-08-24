FROM ghcr.io/astral-sh/uv:0.11.2 AS uv

FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

COPY --from=uv /uv /uvx /bin/
COPY pyproject.toml uv.lock ./
RUN uv sync --locked --no-dev --no-install-project

COPY src ./src
RUN uv sync --locked --no-dev

EXPOSE 8000

CMD ["uv", "run", "--no-dev", "uvicorn", "smart_task_ai.main:app", "--host", "0.0.0.0", "--port", "8000"]
