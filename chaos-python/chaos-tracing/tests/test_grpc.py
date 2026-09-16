from __future__ import annotations

import logging
from types import SimpleNamespace

import grpc
import pytest

from chaos_tracing import (
    GrpcServerTraceInterceptor,
    RequestTimingOptions,
    current_request_timing,
    current_trace_context,
    timing_stage,
)

TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
UPSTREAM_SPAN_ID = "00f067aa0ba902b7"


class FakeServicerContext:
    def __init__(self) -> None:
        self.initial_metadata: tuple[tuple[str, str], ...] = ()

    async def send_initial_metadata(self, metadata) -> None:
        self.initial_metadata = tuple(metadata)


@pytest.mark.asyncio
async def test_interceptor_binds_service_span_and_resets_context() -> None:
    observed_contexts = []

    async def service(request, context):
        observed_contexts.append(current_trace_context())
        return request

    handler = grpc.unary_unary_rpc_method_handler(service)

    async def continuation(details):
        return handler

    details = SimpleNamespace(
        invocation_metadata=(
            ("traceparent", f"00-{TRACE_ID}-{UPSTREAM_SPAN_ID}-01"),
        )
    )
    interceptor = GrpcServerTraceInterceptor()

    wrapped_handler = await interceptor.intercept_service(continuation, details)
    fake_context = FakeServicerContext()
    response = await wrapped_handler.unary_unary("payload", fake_context)

    assert response == "payload"
    assert observed_contexts[0].trace_id == TRACE_ID
    assert observed_contexts[0].span_id != UPSTREAM_SPAN_ID
    assert observed_contexts[0].parent_span_id == UPSTREAM_SPAN_ID
    assert fake_context.initial_metadata == (("x-request-id", TRACE_ID),)
    assert current_trace_context() is None


@pytest.mark.asyncio
async def test_interceptor_binds_request_timing_and_logs_completed_call(caplog) -> None:
    caplog.set_level(logging.INFO, logger="chaos_tracing.grpc")
    observed_timing = []

    async def service(request, context):
        observed_timing.append(current_request_timing())
        with timing_stage("clickhouse"):
            return request

    handler = grpc.unary_unary_rpc_method_handler(service)

    async def continuation(details):
        return handler

    details = SimpleNamespace(
        invocation_metadata=(
            ("traceparent", f"00-{TRACE_ID}-{UPSTREAM_SPAN_ID}-01"),
        ),
        method="/test.Service/Get",
    )
    interceptor = GrpcServerTraceInterceptor(
        request_timing=RequestTimingOptions(
            enabled=True,
            service_name="python-service",
            environment="test",
        )
    )

    wrapped_handler = await interceptor.intercept_service(continuation, details)
    await wrapped_handler.unary_unary("payload", FakeServicerContext())

    assert observed_timing[0] is not None
    assert current_request_timing() is None
    assert "event=request_timing" in caplog.text
    assert f"trace_id={TRACE_ID}" in caplog.text
    assert "clickhouse" in caplog.text


def test_interceptor_requires_bind_and_reset_hooks_together() -> None:
    with pytest.raises(ValueError, match="configured together"):
        GrpcServerTraceInterceptor(bind_context=lambda context: object())


@pytest.mark.asyncio
async def test_handler_can_send_its_own_initial_metadata() -> None:
    async def service(request, context):
        await context.send_initial_metadata((("x-custom", "1"),))
        return request

    handler = grpc.unary_unary_rpc_method_handler(service)

    async def continuation(details):
        return handler

    details = SimpleNamespace(
        invocation_metadata=(("traceparent", f"00-{TRACE_ID}-{UPSTREAM_SPAN_ID}-01"),)
    )
    fake_context = StrictServicerContext()
    wrapped_handler = await GrpcServerTraceInterceptor().intercept_service(continuation, details)

    await wrapped_handler.unary_unary("payload", fake_context)

    assert fake_context.sent_count == 1
    assert fake_context.initial_metadata == (("x-custom", "1"), ("x-request-id", TRACE_ID))


@pytest.mark.asyncio
async def test_streaming_handler_sends_request_id_before_first_response() -> None:
    async def service(request, context):
        yield "first"
        yield "second"

    handler = grpc.unary_stream_rpc_method_handler(service)

    async def continuation(details):
        return handler

    details = SimpleNamespace(invocation_metadata=())
    fake_context = StrictServicerContext()
    wrapped_handler = await GrpcServerTraceInterceptor().intercept_service(continuation, details)

    responses = [response async for response in wrapped_handler.unary_stream("payload", fake_context)]

    assert responses == ["first", "second"]
    assert fake_context.sent_count == 1
    assert fake_context.initial_metadata[0][0] == "x-request-id"


class StrictServicerContext(FakeServicerContext):
    """Mimics grpc.aio: initial metadata can only be sent once."""

    def __init__(self) -> None:
        super().__init__()
        self.sent_count = 0

    async def send_initial_metadata(self, metadata) -> None:
        if self.sent_count:
            raise RuntimeError("initial metadata already sent")
        self.sent_count += 1
        await super().send_initial_metadata(metadata)
