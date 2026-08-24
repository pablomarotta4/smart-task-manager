from __future__ import annotations

import re
from contextvars import ContextVar
from uuid import uuid4

from starlette.datastructures import Headers, MutableHeaders
from starlette.responses import PlainTextResponse
from starlette.types import ASGIApp, Message, Receive, Scope, Send

CORRELATION_ID_HEADER = "X-Correlation-ID"
_SAFE_CORRELATION_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,127}\Z")
_correlation_id: ContextVar[str | None] = ContextVar("correlation_id", default=None)


def get_correlation_id() -> str | None:
    return _correlation_id.get()


def _resolve_correlation_id(candidate: str | None) -> str:
    if candidate is not None and _SAFE_CORRELATION_ID.fullmatch(candidate):
        return candidate
    return str(uuid4())


class CorrelationIdMiddleware:
    def __init__(self, app: ASGIApp) -> None:
        self._app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self._app(scope, receive, send)
            return

        correlation_id = _resolve_correlation_id(Headers(scope=scope).get(CORRELATION_ID_HEADER))
        context_token = _correlation_id.set(correlation_id)
        response_started = False

        async def send_with_correlation_id(message: Message) -> None:
            nonlocal response_started
            if message["type"] == "http.response.start":
                response_started = True
                response_headers = MutableHeaders(scope=message)
                response_headers[CORRELATION_ID_HEADER] = correlation_id
            await send(message)

        try:
            await self._app(scope, receive, send_with_correlation_id)
        except Exception:
            if not response_started:
                response = PlainTextResponse(
                    "Internal Server Error",
                    status_code=500,
                    headers={CORRELATION_ID_HEADER: correlation_id},
                )
                await response(scope, receive, send_with_correlation_id)
            raise
        finally:
            _correlation_id.reset(context_token)
