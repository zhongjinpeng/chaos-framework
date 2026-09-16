from __future__ import annotations

import asyncio

import pytest

from chaos_tracing.timing import (
    RequestTiming,
    RequestTimingOptions,
    bind_request_timing,
    current_request_timing,
    reset_request_timing,
    timing_stage,
)


def test_request_timing_is_enabled_by_default_and_uses_single_switch(monkeypatch) -> None:
    monkeypatch.delenv("REQUEST_TIMING_ENABLED", raising=False)
    assert RequestTimingOptions.from_environment("python-service").enabled

    monkeypatch.setenv("REQUEST_TIMING_ENABLED", "false")
    assert not RequestTimingOptions.from_environment("python-service").enabled


def test_timing_stage_accumulates_and_collapses_nested_same_stage() -> None:
    timing = RequestTiming()
    token = bind_request_timing(timing)
    try:
        with timing_stage("ClickHouse"), timing_stage("ClickHouse"):
            assert current_request_timing() is timing
    finally:
        reset_request_timing(token)

    snapshot = timing.snapshot()
    assert snapshot.stage_counts == {"clickhouse": 1}
    assert snapshot.stage_millis["clickhouse"] >= 0
    assert current_request_timing() is None


def test_timing_stage_records_failed_operation() -> None:
    timing = RequestTiming()
    token = bind_request_timing(timing)
    try:
        with pytest.raises(RuntimeError, match="redis unavailable"), timing_stage(
            "redis"
        ):
            raise RuntimeError("redis unavailable")
    finally:
        reset_request_timing(token)

    assert timing.snapshot().stage_counts == {"redis": 1}


@pytest.mark.asyncio
async def test_request_timing_context_is_isolated_between_concurrent_tasks() -> None:
    async def run(stage_name: str) -> RequestTiming:
        timing = RequestTiming()
        token = bind_request_timing(timing)
        try:
            await asyncio.sleep(0)
            with timing_stage(stage_name):
                await asyncio.sleep(0)
            assert current_request_timing() is timing
            return timing
        finally:
            reset_request_timing(token)

    clickhouse, redis = await asyncio.gather(run("clickhouse"), run("redis"))

    assert clickhouse.snapshot().stage_counts == {"clickhouse": 1}
    assert redis.snapshot().stage_counts == {"redis": 1}
    assert current_request_timing() is None
