"""Async gRPC server tracing interceptor."""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator, Awaitable, Callable, Iterable, Iterator
from contextlib import contextmanager
from typing import Any

import grpc

from chaos_tracing.context import (
    REQUEST_ID_HEADER,
    TraceContext,
    bind_trace_context,
    current_trace_context,
    reset_trace_context,
    resolve_inbound_trace_context,
)
from chaos_tracing.timing import (
    RequestTiming,
    RequestTimingOptions,
    bind_request_timing,
    reset_request_timing,
)

logger = logging.getLogger(__name__)

_SLOW_REQUEST_THRESHOLD_MS = 1000.0

BindContextHook = Callable[[TraceContext], object]
ResetContextHook = Callable[[object], None]


class _RequestIdMetadataContext:
    """ServicerContext proxy that merges the request id into initial metadata.

    gRPC allows initial metadata to be sent only once. The previous implementation
    sent it before invoking the handler, so any handler calling
    ``send_initial_metadata`` itself failed. This proxy merges the request id into
    the handler's own call, or sends it lazily before the first response.
    """

    def __init__(self, delegate: grpc.aio.ServicerContext, request_id: str) -> None:
        self._delegate = delegate
        self._request_id = request_id
        self._initial_metadata_sent = False

    def __getattr__(self, name: str) -> Any:
        return getattr(self._delegate, name)

    async def send_initial_metadata(self, initial_metadata: Iterable[tuple[str, str]] | None) -> None:
        merged = tuple(initial_metadata or ())
        if self._request_id and not any(str(key).lower() == REQUEST_ID_HEADER for key, _ in merged):
            merged = (*merged, (REQUEST_ID_HEADER, self._request_id))
        self._initial_metadata_sent = True
        await self._delegate.send_initial_metadata(merged)

    async def ensure_initial_metadata_sent(self) -> None:
        if self._initial_metadata_sent or not self._request_id:
            return
        await self.send_initial_metadata(())


class GrpcServerTraceInterceptor(grpc.aio.ServerInterceptor):  # type: ignore[misc]
    """Create an independent service Span for every inbound gRPC call."""

    def __init__(
        self,
        bind_context: BindContextHook | None = None,
        reset_context: ResetContextHook | None = None,
        expose_request_id: bool = True,
        request_timing: RequestTimingOptions | None = None,
    ) -> None:
        if (bind_context is None) != (reset_context is None):
            raise ValueError("bind_context and reset_context must be configured together")
        self._bind_context = bind_context
        self._reset_context = reset_context
        self._expose_request_id = expose_request_id
        self._request_timing = request_timing or RequestTimingOptions()

    async def intercept_service(
        self,
        continuation: Callable[..., Awaitable[grpc.RpcMethodHandler | None]],
        handler_call_details: grpc.HandlerCallDetails,
    ) -> grpc.RpcMethodHandler | None:
        handler = await continuation(handler_call_details)
        if handler is None:
            return None

        trace_context = resolve_inbound_trace_context(
            handler_call_details.invocation_metadata or ()
        )
        operation = getattr(handler_call_details, "method", "") or "unknown"
        if handler.unary_unary:
            return grpc.unary_unary_rpc_method_handler(
                self._wrap_unary_unary(handler.unary_unary, trace_context, operation),
                request_deserializer=handler.request_deserializer,
                response_serializer=handler.response_serializer,
            )
        if handler.unary_stream:
            return grpc.unary_stream_rpc_method_handler(
                self._wrap_unary_stream(handler.unary_stream, trace_context, operation),
                request_deserializer=handler.request_deserializer,
                response_serializer=handler.response_serializer,
            )
        if handler.stream_unary:
            return grpc.stream_unary_rpc_method_handler(
                self._wrap_stream_unary(handler.stream_unary, trace_context, operation),
                request_deserializer=handler.request_deserializer,
                response_serializer=handler.response_serializer,
            )
        if handler.stream_stream:
            return grpc.stream_stream_rpc_method_handler(
                self._wrap_stream_stream(handler.stream_stream, trace_context, operation),
                request_deserializer=handler.request_deserializer,
                response_serializer=handler.response_serializer,
            )
        return handler

    def _wrap_unary_unary(
        self,
        handler: Callable[..., Awaitable[Any]],
        trace_context: TraceContext,
        operation: str,
    ) -> Callable[..., Awaitable[Any]]:
        async def wrapped(request: Any, context: grpc.aio.ServicerContext) -> Any:
            metadata_context = self._metadata_context(context, trace_context)
            with self._bound_request(trace_context, operation, context):
                response = await handler(request, metadata_context)
                await metadata_context.ensure_initial_metadata_sent()
                return response

        return wrapped

    def _wrap_unary_stream(
        self,
        handler: Callable[..., AsyncIterator[Any]],
        trace_context: TraceContext,
        operation: str,
    ) -> Callable[..., AsyncIterator[Any]]:
        async def wrapped(
            request: Any,
            context: grpc.aio.ServicerContext,
        ) -> AsyncIterator[Any]:
            metadata_context = self._metadata_context(context, trace_context)
            with self._bound_request(trace_context, operation, context):
                async for response in handler(request, metadata_context):
                    await metadata_context.ensure_initial_metadata_sent()
                    yield response
                await metadata_context.ensure_initial_metadata_sent()

        return wrapped

    def _wrap_stream_unary(
        self,
        handler: Callable[..., Awaitable[Any]],
        trace_context: TraceContext,
        operation: str,
    ) -> Callable[..., Awaitable[Any]]:
        async def wrapped(
            request_iterator: AsyncIterator[Any],
            context: grpc.aio.ServicerContext,
        ) -> Any:
            metadata_context = self._metadata_context(context, trace_context)
            with self._bound_request(trace_context, operation, context):
                response = await handler(request_iterator, metadata_context)
                await metadata_context.ensure_initial_metadata_sent()
                return response

        return wrapped

    def _wrap_stream_stream(
        self,
        handler: Callable[..., AsyncIterator[Any]],
        trace_context: TraceContext,
        operation: str,
    ) -> Callable[..., AsyncIterator[Any]]:
        async def wrapped(
            request_iterator: AsyncIterator[Any],
            context: grpc.aio.ServicerContext,
        ) -> AsyncIterator[Any]:
            metadata_context = self._metadata_context(context, trace_context)
            with self._bound_request(trace_context, operation, context):
                async for response in handler(request_iterator, metadata_context):
                    await metadata_context.ensure_initial_metadata_sent()
                    yield response
                await metadata_context.ensure_initial_metadata_sent()

        return wrapped

    def _metadata_context(
        self,
        context: grpc.aio.ServicerContext,
        trace_context: TraceContext,
    ) -> _RequestIdMetadataContext:
        request_id = trace_context.request_id if self._expose_request_id else ""
        return _RequestIdMetadataContext(context, request_id)

    @contextmanager
    def _bound_context(self, trace_context: TraceContext) -> Iterator[None]:
        trace_token = bind_trace_context(trace_context)
        hook_token: object | None = None
        try:
            if self._bind_context is not None:
                hook_token = self._bind_context(trace_context)
            yield
        finally:
            try:
                if self._reset_context is not None and hook_token is not None:
                    self._reset_context(hook_token)
            finally:
                reset_trace_context(trace_token)

    @contextmanager
    def _bound_request(
        self,
        trace_context: TraceContext,
        operation: str,
        grpc_context: grpc.aio.ServicerContext,
    ) -> Iterator[None]:
        with self._bound_context(trace_context):
            if not self._timing_enabled():
                yield
                return

            timing = RequestTiming()
            timing_token = bind_request_timing(timing)
            failure: BaseException | None = None
            try:
                yield
            except BaseException as exc:
                failure = exc
                raise
            finally:
                try:
                    self._log_request_timing(operation, grpc_context, timing, failure)
                finally:
                    reset_request_timing(timing_token)

    def _timing_enabled(self) -> bool:
        return self._request_timing.enabled

    def _log_request_timing(
        self,
        operation: str,
        grpc_context: grpc.aio.ServicerContext,
        timing: RequestTiming,
        failure: BaseException | None,
    ) -> None:
        snapshot = timing.snapshot()
        trace_context = current_trace_context()
        trace_id = trace_context.trace_id if trace_context is not None else ""
        request_id = trace_context.request_id if trace_context is not None else ""
        status = self._status_name(grpc_context, failure)
        failed = failure is not None or status not in {"OK", "UNKNOWN"}
        slow = snapshot.total_ms >= _SLOW_REQUEST_THRESHOLD_MS
        message = (
            "event=request_timing env=%s service=%s protocol=grpc operation=%s "
            "status=%s trace_id=%s request_id=%s total_ms=%s stages=%s counts=%s "
            "slow=%s slowest_stage=%s"
        )
        arguments = (
            self._request_timing.environment,
            self._request_timing.service_name,
            operation,
            status,
            trace_id,
            request_id,
            snapshot.total_ms,
            snapshot.stage_millis,
            snapshot.stage_counts,
            str(slow).lower(),
            snapshot.slowest_stage,
        )
        extra = {
            "event": "request_timing",
            "environment": self._request_timing.environment,
            "service": self._request_timing.service_name,
            "protocol": "grpc",
            "operation": operation,
            "status": status,
            "trace_id": trace_id,
            "request_id": request_id,
            "total_ms": snapshot.total_ms,
            "stages": snapshot.stage_millis,
            "counts": snapshot.stage_counts,
            "slow": slow,
            "slowest_stage": snapshot.slowest_stage,
        }
        if failed or slow:
            logger.warning(message, *arguments, extra=extra)
        else:
            logger.info(message, *arguments, extra=extra)

    @staticmethod
    def _status_name(
        grpc_context: grpc.aio.ServicerContext,
        failure: BaseException | None,
    ) -> str:
        if failure is not None:
            return type(failure).__name__
        code_method = getattr(grpc_context, "code", None)
        code = code_method() if callable(code_method) else None
        return getattr(code, "name", "OK") if code is not None else "OK"
