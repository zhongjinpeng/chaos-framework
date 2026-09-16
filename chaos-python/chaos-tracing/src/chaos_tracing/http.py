"""ASGI/WSGI trace propagation middleware and outbound header helpers.

Why this module exists:
    The Java side propagates trace context over HTTP everywhere (web, gateway, Feign, MQ),
    but this package only shipped a gRPC server interceptor. A Python service sitting behind
    the chaos gateway is reached over HTTP, so it could never pick up the inbound traceparent
    and its logs could not be correlated with the rest of the request.

Both middlewares are framework agnostic: the ASGI one works with FastAPI, Starlette and any
other ASGI app, the WSGI one with Flask, Django and friends. Neither imports a web framework.
"""

from __future__ import annotations

from collections.abc import Awaitable, Callable, Iterable, MutableMapping
from typing import Any

from chaos_tracing.context import (
    REQUEST_ID_HEADER,
    SPAN_ID_HEADER,
    TRACE_ID_HEADER,
    TraceContext,
    bind_trace_context,
    build_outgoing_metadata,
    reset_trace_context,
    resolve_inbound_trace_context,
)

Scope = MutableMapping[str, Any]
Receive = Callable[[], Awaitable[MutableMapping[str, Any]]]
Send = Callable[[MutableMapping[str, Any]], Awaitable[None]]


def build_outgoing_headers(context: TraceContext | None = None) -> dict[str, str]:
    """Return outbound HTTP headers carrying the current trace context.

    Use with any HTTP client, for example ``httpx.get(url, headers=build_outgoing_headers())``.
    """
    return {name: value for name, value in build_outgoing_metadata(context)}


def _headers_from_asgi(scope: Scope) -> dict[str, str]:
    raw: Iterable[tuple[bytes, bytes]] = scope.get("headers") or ()
    return {
        name.decode("latin-1").lower(): value.decode("latin-1")
        for name, value in raw
    }


def _headers_from_wsgi(environ: MutableMapping[str, Any]) -> dict[str, str]:
    headers: dict[str, str] = {}
    for key, value in environ.items():
        if key.startswith("HTTP_") and isinstance(value, str):
            headers[key[5:].replace("_", "-").lower()] = value
    return headers


def _response_headers(context: TraceContext) -> list[tuple[str, str]]:
    """Echo identifiers back so callers can correlate a response with server logs."""
    headers = [(TRACE_ID_HEADER, context.trace_id), (SPAN_ID_HEADER, context.span_id)]
    if context.request_id:
        headers.append((REQUEST_ID_HEADER, context.request_id))
    return headers


class TraceContextASGIMiddleware:
    """ASGI middleware binding an inbound trace context for the duration of the request.

    ``contextvars`` are per-task, so the bound context is visible to every ``await`` in the
    handler and is automatically isolated between concurrent requests. The token is reset in
    ``finally`` so a task that reuses the same context does not inherit a stale trace.
    """

    def __init__(self, app: Callable[[Scope, Receive, Send], Awaitable[None]]) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope.get("type") != "http":
            await self.app(scope, receive, send)
            return

        context = resolve_inbound_trace_context(_headers_from_asgi(scope))
        token = bind_trace_context(context)

        async def send_with_trace(message: MutableMapping[str, Any]) -> None:
            if message.get("type") == "http.response.start":
                headers = list(message.get("headers") or ())
                headers.extend(
                    (name.encode("latin-1"), value.encode("latin-1"))
                    for name, value in _response_headers(context)
                )
                message = {**message, "headers": headers}
            await send(message)

        try:
            await self.app(scope, receive, send_with_trace)
        finally:
            reset_trace_context(token)


class TraceContextWSGIMiddleware:
    """WSGI middleware binding an inbound trace context for the duration of the request."""

    def __init__(self, app: Callable[..., Iterable[bytes]]) -> None:
        self.app = app

    def __call__(
        self,
        environ: MutableMapping[str, Any],
        start_response: Callable[..., Any],
    ) -> Iterable[bytes]:
        context = resolve_inbound_trace_context(_headers_from_wsgi(environ))
        token = bind_trace_context(context)

        def start_response_with_trace(
            status: str,
            headers: list[tuple[str, str]],
            exc_info: Any = None,
        ) -> Any:
            merged = list(headers) + _response_headers(context)
            if exc_info is not None:
                return start_response(status, merged, exc_info)
            return start_response(status, merged)

        try:
            # The body may be a generator consumed after this call returns, so materialise it
            # while the context is still bound; otherwise trace ids vanish mid-response.
            return list(self.app(environ, start_response_with_trace))
        finally:
            reset_trace_context(token)
