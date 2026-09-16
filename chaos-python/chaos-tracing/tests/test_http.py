"""Tests for the HTTP trace propagation middlewares."""

from __future__ import annotations

import asyncio
import contextlib
from typing import Any

from chaos_tracing import (
    TraceContextASGIMiddleware,
    TraceContextWSGIMiddleware,
    build_outgoing_headers,
    current_trace_context,
)

TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-a1b2c3d4e5f60718-01"


def _asgi_scope(headers: list[tuple[bytes, bytes]]) -> dict[str, Any]:
    return {"type": "http", "method": "GET", "path": "/orders", "headers": headers}


def _run_asgi(app: Any, scope: dict[str, Any]) -> list[dict[str, Any]]:
    sent: list[dict[str, Any]] = []

    async def receive() -> dict[str, Any]:
        return {"type": "http.request"}

    async def send(message: dict[str, Any]) -> None:
        sent.append(message)

    asyncio.run(app(scope, receive, send))
    return sent


def test_asgi_middleware_adopts_inbound_traceparent() -> None:
    seen: dict[str, str] = {}

    async def app(scope: dict[str, Any], receive: Any, send: Any) -> None:
        context = current_trace_context()
        assert context is not None
        seen["trace_id"] = context.trace_id
        seen["parent_span_id"] = context.parent_span_id
        await send({"type": "http.response.start", "status": 200, "headers": []})
        await send({"type": "http.response.body", "body": b"{}"})

    _run_asgi(
        TraceContextASGIMiddleware(app),
        _asgi_scope([(b"traceparent", TRACEPARENT.encode())]),
    )

    assert seen["trace_id"] == "4bf92f3577b34da6a3ce929d0e0e4736"
    assert seen["parent_span_id"] == "a1b2c3d4e5f60718"


def test_asgi_middleware_generates_trace_when_absent() -> None:
    seen: dict[str, str] = {}

    async def app(scope: dict[str, Any], receive: Any, send: Any) -> None:
        context = current_trace_context()
        assert context is not None
        seen["trace_id"] = context.trace_id
        await send({"type": "http.response.start", "status": 200, "headers": []})

    _run_asgi(TraceContextASGIMiddleware(app), _asgi_scope([]))

    assert len(seen["trace_id"]) == 32


def test_asgi_middleware_echoes_identifiers_and_clears_context() -> None:
    async def app(scope: dict[str, Any], receive: Any, send: Any) -> None:
        await send({"type": "http.response.start", "status": 200, "headers": []})

    sent = _run_asgi(
        TraceContextASGIMiddleware(app),
        _asgi_scope([(b"traceparent", TRACEPARENT.encode())]),
    )

    headers = dict(sent[0]["headers"])
    assert headers[b"x-trace-id"] == b"4bf92f3577b34da6a3ce929d0e0e4736"
    assert b"x-span-id" in headers
    # The context must not leak to whatever runs next on this task.
    assert current_trace_context() is None


def test_asgi_middleware_passes_through_non_http_scopes() -> None:
    called: list[str] = []

    async def app(scope: dict[str, Any], receive: Any, send: Any) -> None:
        called.append(scope["type"])

    asyncio.run(
        TraceContextASGIMiddleware(app)(
            {"type": "lifespan"},
            lambda: asyncio.sleep(0, {"type": "lifespan.startup"}),  # type: ignore[arg-type,return-value]
            lambda message: asyncio.sleep(0),  # type: ignore[arg-type,return-value]
        )
    )

    assert called == ["lifespan"]


def test_asgi_middleware_clears_context_on_error() -> None:
    async def app(scope: dict[str, Any], receive: Any, send: Any) -> None:
        raise RuntimeError("boom")

    with contextlib.suppress(RuntimeError):
        _run_asgi(TraceContextASGIMiddleware(app), _asgi_scope([]))

    assert current_trace_context() is None


def test_wsgi_middleware_binds_and_echoes() -> None:
    captured: dict[str, Any] = {}

    def app(environ: dict[str, Any], start_response: Any) -> list[bytes]:
        context = current_trace_context()
        assert context is not None
        captured["trace_id"] = context.trace_id
        start_response("200 OK", [("Content-Type", "application/json")])
        return [b"{}"]

    def start_response(status: str, headers: list[tuple[str, str]]) -> None:
        captured["headers"] = dict(headers)

    body = TraceContextWSGIMiddleware(app)(
        {"HTTP_TRACEPARENT": TRACEPARENT},
        start_response,
    )

    assert list(body) == [b"{}"]
    assert captured["trace_id"] == "4bf92f3577b34da6a3ce929d0e0e4736"
    assert captured["headers"]["x-trace-id"] == "4bf92f3577b34da6a3ce929d0e0e4736"
    assert current_trace_context() is None


def test_build_outgoing_headers_carries_current_context() -> None:
    async def app(scope: dict[str, Any], receive: Any, send: Any) -> None:
        headers = build_outgoing_headers()
        assert headers["traceparent"].startswith("00-4bf92f3577b34da6a3ce929d0e0e4736-")
        await send({"type": "http.response.start", "status": 200, "headers": []})

    _run_asgi(
        TraceContextASGIMiddleware(app),
        _asgi_scope([(b"traceparent", TRACEPARENT.encode())]),
    )
