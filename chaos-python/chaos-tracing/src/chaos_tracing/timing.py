"""Request timing context and compact stage aggregation."""

from __future__ import annotations

import os
import re
import threading
import time
from collections.abc import Iterator
from contextlib import contextmanager
from contextvars import ContextVar, Token
from dataclasses import dataclass, field

_MAX_STAGE_NAME_LENGTH = 64
_INVALID_STAGE_CHARACTER = re.compile(r"[^a-z0-9_.-]")
_CURRENT_REQUEST_TIMING: ContextVar[RequestTiming | None] = ContextVar(
    "chaos_request_timing",
    default=None,
)
_ACTIVE_STAGES: ContextVar[tuple[str, ...]] = ContextVar(
    "chaos_request_timing_active_stages",
    default=(),
)


@dataclass(frozen=True, slots=True)
class RequestTimingOptions:
    """Runtime configuration for one-line request timing logs."""

    enabled: bool = True
    service_name: str = "application"
    environment: str = "default"

    @classmethod
    def from_environment(cls, service_name: str) -> RequestTimingOptions:
        """Read the single cross-language request timing switch."""
        return cls(
            enabled=_environment_bool("REQUEST_TIMING_ENABLED", True),
            service_name=service_name,
            environment=os.getenv("APP_ENV", os.getenv("ENVIRONMENT", "default")),
        )


@dataclass(frozen=True, slots=True)
class StageTiming:
    """One accumulated stage value."""

    total_ns: int
    count: int

    @property
    def total_ms(self) -> float:
        return _milliseconds(self.total_ns)


@dataclass(frozen=True, slots=True)
class RequestTimingSnapshot:
    """Immutable point-in-time timing snapshot."""

    total_ns: int
    stages: dict[str, StageTiming]
    slowest_stage: str

    @property
    def total_ms(self) -> float:
        return _milliseconds(self.total_ns)

    @property
    def stage_millis(self) -> dict[str, float]:
        return {name: stage.total_ms for name, stage in self.stages.items()}

    @property
    def stage_counts(self) -> dict[str, int]:
        return {name: stage.count for name, stage in self.stages.items()}


@dataclass(slots=True)
class RequestTiming:
    """Thread-safe monotonic timing accumulator for one request."""

    started_at_ns: int = field(default_factory=time.perf_counter_ns)
    _stages: dict[str, list[int]] = field(default_factory=dict, init=False)
    _lock: threading.Lock = field(default_factory=threading.Lock, init=False)

    def record(self, stage_name: str, duration_ns: int) -> None:
        """Record one completed stage duration."""
        normalized = _normalize_stage_name(stage_name)
        if not normalized:
            return
        with self._lock:
            value = self._stages.setdefault(normalized, [0, 0])
            value[0] += max(0, duration_ns)
            value[1] += 1

    def snapshot(self) -> RequestTimingSnapshot:
        """Return an immutable stage snapshot sorted by stage name."""
        with self._lock:
            stages = {
                name: StageTiming(total_ns=value[0], count=value[1])
                for name, value in sorted(self._stages.items())
            }
        slowest = max(stages, key=lambda name: stages[name].total_ns, default="")
        return RequestTimingSnapshot(
            total_ns=max(0, time.perf_counter_ns() - self.started_at_ns),
            stages=stages,
            slowest_stage=slowest,
        )


@dataclass(frozen=True, slots=True)
class _RequestTimingToken:
    timing: Token[RequestTiming | None]
    active_stages: Token[tuple[str, ...]]


def current_request_timing() -> RequestTiming | None:
    """Return the timing accumulator bound to the current async context."""
    return _CURRENT_REQUEST_TIMING.get()


def bind_request_timing(timing: RequestTiming) -> _RequestTimingToken:
    """Bind a request timing accumulator and return its reset token."""
    active_stages_token = _ACTIVE_STAGES.set(())
    timing_token = _CURRENT_REQUEST_TIMING.set(timing)
    return _RequestTimingToken(timing=timing_token, active_stages=active_stages_token)


def reset_request_timing(token: _RequestTimingToken) -> None:
    """Restore the request timing context that existed before binding."""
    _CURRENT_REQUEST_TIMING.reset(token.timing)
    _ACTIVE_STAGES.reset(token.active_stages)


@contextmanager
def timing_stage(stage_name: str) -> Iterator[None]:
    """Measure one stage if a request timing context is active."""
    timing = current_request_timing()
    normalized = _normalize_stage_name(stage_name)
    if timing is None or not normalized:
        yield
        return

    active_stages = _ACTIVE_STAGES.get()
    record_duration = normalized not in active_stages
    active_token = _ACTIVE_STAGES.set((*active_stages, normalized))
    started_at_ns = time.perf_counter_ns()
    try:
        yield
    finally:
        _ACTIVE_STAGES.reset(active_token)
        if record_duration:
            timing.record(normalized, time.perf_counter_ns() - started_at_ns)


def _normalize_stage_name(value: str) -> str:
    normalized = _INVALID_STAGE_CHARACTER.sub("_", str(value).strip().lower())
    return normalized[:_MAX_STAGE_NAME_LENGTH]


def _milliseconds(nanoseconds: int) -> float:
    return round(nanoseconds / 1_000_000.0, 3)


def _environment_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}
