from chaos_tracing import (
    bind_trace_context,
    build_outgoing_metadata,
    current_trace_context,
    reset_trace_context,
    resolve_inbound_trace_context,
)

TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
UPSTREAM_SPAN_ID = "00f067aa0ba902b7"


def test_inherits_w3c_trace_and_creates_independent_service_span() -> None:
    context = resolve_inbound_trace_context(
        (
            ("x-trace-id", "legacy-trace"),
            ("x-span-id", UPSTREAM_SPAN_ID),
            ("traceparent", f"00-{TRACE_ID}-{UPSTREAM_SPAN_ID}-01"),
            ("tracestate", "vendor=state"),
            ("baggage", "tenant=acme"),
        )
    )

    assert context.trace_id == TRACE_ID
    assert context.parent_span_id == UPSTREAM_SPAN_ID
    assert context.span_id != UPSTREAM_SPAN_ID
    assert len(context.span_id) == 16
    assert context.traceparent == f"00-{TRACE_ID}-{context.span_id}-01"
    assert context.trace_state == "vendor=state"
    assert context.baggage == "tenant=acme"


def test_creates_full_identifiers_when_metadata_is_missing() -> None:
    context = resolve_inbound_trace_context(None)

    assert len(context.trace_id) == 32
    assert len(context.span_id) == 16
    assert context.parent_span_id == ""
    assert context.request_id == context.trace_id


def test_bind_and_reset_restore_previous_context() -> None:
    context = resolve_inbound_trace_context(None)

    token = bind_trace_context(context)
    assert current_trace_context() == context
    reset_trace_context(token)

    assert current_trace_context() is None


def test_outgoing_metadata_creates_new_propagation_span() -> None:
    context = resolve_inbound_trace_context(
        {"traceparent": f"00-{TRACE_ID}-{UPSTREAM_SPAN_ID}-01"}
    )

    metadata = dict(build_outgoing_metadata(context))

    assert metadata["x-trace-id"] == TRACE_ID
    assert metadata["x-span-id"] != context.span_id
    assert metadata["traceparent"] == (
        f"00-{TRACE_ID}-{metadata['x-span-id']}-01"
    )


def test_rejects_unsafe_legacy_trace_and_request_ids() -> None:
    context = resolve_inbound_trace_context(
        (
            ("x-trace-id", "evil\r\nx-injected: 1"),
            ("x-request-id", "<script>"),
            ("tracestate", "vendor=state\n"),
        )
    )

    assert len(context.trace_id) == 32
    assert context.request_id == context.trace_id
    assert context.trace_state == "vendor=state"


def test_parses_future_traceparent_version_forward_compatibly() -> None:
    context = resolve_inbound_trace_context(
        (("traceparent", f"01-{TRACE_ID}-{UPSTREAM_SPAN_ID}-01-extra"),)
    )

    assert context.trace_id == TRACE_ID
    assert context.parent_span_id == UPSTREAM_SPAN_ID
