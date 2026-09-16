"""Trace context parsing, storage, and propagation helpers."""

from __future__ import annotations

import re
import secrets
from collections.abc import Iterable, Mapping
from contextvars import ContextVar, Token
from dataclasses import dataclass

TRACE_ID_HEADER = "x-trace-id"
SPAN_ID_HEADER = "x-span-id"
REQUEST_ID_HEADER = "x-request-id"
TRACEPARENT_HEADER = "traceparent"
TRACESTATE_HEADER = "tracestate"
BAGGAGE_HEADER = "baggage"

_TRACE_ID_KEYS = (TRACE_ID_HEADER, REQUEST_ID_HEADER, "x-correlation-id")
_TRACEPARENT_PATTERN = re.compile(
    r"^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$"
)
_SPAN_ID_PATTERN = re.compile(r"^[0-9a-f]{16}$")
_TRACE_ID_PATTERN = re.compile(r"^[0-9a-f]{32}$")
# Legacy trace ids and request ids come from untrusted clients and are echoed into
# response metadata and logs, so only a conservative character set is accepted.
_LEGACY_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
_MAX_PROPAGATION_VALUE_LENGTH = 8192
_CURRENT_TRACE_CONTEXT: ContextVar[TraceContext | None] = ContextVar(
    "chaos_trace_context",
    default=None,
)

Metadata = Mapping[str, object] | Iterable[tuple[str, object]]


@dataclass(frozen=True, slots=True)
class TraceContext:
    """Immutable trace context for one local service span."""

    trace_id: str
    span_id: str
    parent_span_id: str = ""
    trace_flags: str = "01"
    trace_state: str = ""
    baggage: str = ""
    request_id: str = ""

    @property
    def traceparent(self) -> str:
        """Return W3C traceparent when the trace and span identifiers are valid."""
        if not _TRACE_ID_PATTERN.fullmatch(self.trace_id):
            return ""
        return f"00-{self.trace_id}-{self.span_id}-{self.trace_flags}"


def resolve_inbound_trace_context(metadata: Metadata | None) -> TraceContext:
    """Resolve inbound propagation values and always create a local SpanId."""
    values = _normalize_metadata(metadata)
    parsed_traceparent = _parse_traceparent(values.get(TRACEPARENT_HEADER, ""))

    if parsed_traceparent is not None:
        trace_id, parent_span_id, trace_flags = parsed_traceparent
    else:
        trace_id = _first_valid_legacy_id(values, _TRACE_ID_KEYS) or _new_trace_id()
        parent_span_id = _normalize_span_id(values.get(SPAN_ID_HEADER, ""))
        trace_flags = "01"

    request_id = _sanitize_legacy_id(values.get(REQUEST_ID_HEADER, "")) or trace_id
    return TraceContext(
        trace_id=trace_id,
        span_id=_new_span_id(),
        parent_span_id=parent_span_id,
        trace_flags=trace_flags,
        trace_state=_sanitize_propagation_value(values.get(TRACESTATE_HEADER, "")),
        baggage=_sanitize_propagation_value(values.get(BAGGAGE_HEADER, "")),
        request_id=request_id,
    )


def current_trace_context() -> TraceContext | None:
    """Return the trace context bound to the current asynchronous execution context."""
    return _CURRENT_TRACE_CONTEXT.get()


def bind_trace_context(context: TraceContext) -> Token[TraceContext | None]:
    """Bind a trace context and return the token required to restore it."""
    return _CURRENT_TRACE_CONTEXT.set(context)


def reset_trace_context(token: Token[TraceContext | None]) -> None:
    """Restore the trace context that existed before a bind operation."""
    _CURRENT_TRACE_CONTEXT.reset(token)


def build_outgoing_metadata(context: TraceContext | None = None) -> tuple[tuple[str, str], ...]:
    """Build metadata for a downstream call using a new client propagation SpanId."""
    active_context = context or current_trace_context()
    if active_context is None:
        raise RuntimeError("No trace context is bound")

    outgoing_span_id = _new_span_id()
    metadata = [
        (TRACE_ID_HEADER, active_context.trace_id),
        (SPAN_ID_HEADER, outgoing_span_id),
        (REQUEST_ID_HEADER, active_context.request_id or active_context.trace_id),
    ]
    if _TRACE_ID_PATTERN.fullmatch(active_context.trace_id):
        metadata.append(
            (
                TRACEPARENT_HEADER,
                f"00-{active_context.trace_id}-{outgoing_span_id}-{active_context.trace_flags}",
            )
        )
    if active_context.trace_state:
        metadata.append((TRACESTATE_HEADER, active_context.trace_state))
    if active_context.baggage:
        metadata.append((BAGGAGE_HEADER, active_context.baggage))
    return tuple(metadata)


def _normalize_metadata(metadata: Metadata | None) -> dict[str, str]:
    if metadata is None:
        return {}
    items = metadata.items() if isinstance(metadata, Mapping) else metadata
    normalized: dict[str, str] = {}
    for raw_key, raw_value in items:
        key = str(raw_key).strip().lower()
        if not key or key in normalized:
            continue
        if isinstance(raw_value, bytes):
            value = raw_value.decode("utf-8", errors="replace").strip()
        else:
            value = str(raw_value).strip()
        normalized[key] = value
    return normalized


def _parse_traceparent(value: str) -> tuple[str, str, str] | None:
    normalized = value.strip().lower()
    parts = normalized.split("-")
    # W3C forward compatibility: a higher version is parsed as version 00 using the
    # first four fields; version ff is invalid.
    if len(parts) > 4 and re.fullmatch(r"[0-9a-f]{2}", parts[0]) and parts[0] not in ("00", "ff"):
        normalized = "00-" + "-".join(parts[1:4])
    match = _TRACEPARENT_PATTERN.fullmatch(normalized)
    if match is None:
        return None
    trace_id, parent_span_id, trace_flags = match.groups()
    if trace_id == "0" * 32 or parent_span_id == "0" * 16:
        return None
    return trace_id, parent_span_id, trace_flags


def _normalize_span_id(value: str) -> str:
    normalized = value.strip().lower()
    if normalized == "0" * 16 or not _SPAN_ID_PATTERN.fullmatch(normalized):
        return ""
    return normalized


def _first_valid_legacy_id(values: Mapping[str, str], keys: tuple[str, ...]) -> str:
    candidates = (_sanitize_legacy_id(values.get(key, "")) for key in keys)
    return next((candidate for candidate in candidates if candidate), "")


def _sanitize_legacy_id(value: str) -> str:
    """Accept only safe legacy identifiers; invalid values are treated as missing."""
    candidate = value.strip()
    return candidate if _LEGACY_ID_PATTERN.fullmatch(candidate) else ""


def _sanitize_propagation_value(value: str) -> str:
    """Drop control characters and oversized propagation headers."""
    cleaned = "".join(character for character in value if character.isprintable()).strip()
    return cleaned if len(cleaned) <= _MAX_PROPAGATION_VALUE_LENGTH else ""


def _new_trace_id() -> str:
    return _random_non_zero_hex(16)


def _new_span_id() -> str:
    return _random_non_zero_hex(8)


def _random_non_zero_hex(byte_count: int) -> str:
    while True:
        value = secrets.token_hex(byte_count)
        if any(character != "0" for character in value):
            return value
