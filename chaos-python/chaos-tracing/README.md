# chaos-tracing

Chaos Framework 的 Python 链路上下文包，版本与框架保持 `1.0.0`。

核心约定：

- 整条调用链继承同一个 TraceId。
- 每个服务端入口始终创建新的本地 SpanId。
- 上游 SpanId 从 W3C `traceparent` 解析为 `parent_span_id`，不复用为本地 SpanId。
- gRPC 响应只返回 `x-request-id`，不向外暴露内部 SpanId 和 W3C 传播头。

```python
from chaos_tracing import GrpcServerTraceInterceptor

server = grpc.aio.server(interceptors=[GrpcServerTraceInterceptor()])
```

应用已有日志上下文时，可以使用绑定钩子：

```python
from chaos_tracing import GrpcServerTraceInterceptor

interceptor = GrpcServerTraceInterceptor(
    bind_context=lambda context: bind_log_context(
        request_id=context.request_id,
        trace_id=context.trace_id,
        span_id=context.span_id,
    ),
    reset_context=unbind_log_context,
)
```

## 安全与兼容约定

- `x-trace-id`、`x-request-id`、`x-correlation-id` 来自不可信调用方，只接受 `[A-Za-z0-9_-]{1,64}`，
  非法值视为缺失并重新生成，防止日志注入和响应 metadata 注入。
- `tracestate`、`baggage` 会移除控制字符，超过 8192 字符直接丢弃。
- 更高版本的 `traceparent`（例如 `01-...-extra`）按 W3C 规范取前 4 段解析，`ff` 版本视为非法。
- `x-request-id` 通过 initial metadata 返回：若业务 handler 自己调用 `send_initial_metadata`，
  拦截器会把 request id 合并进同一次发送；否则在首个响应前补发。gRPC 只允许发送一次 initial metadata，
  旧版本在调用 handler 前抢先发送，会导致 handler 自己发送时报错。

## 测试

```bash
pip install -e ".[test]"
pytest
```

`pyproject.toml` 已配置 `pythonpath = ["src"]`，测试始终针对源码运行，不会被旧的安装包遮蔽。

## HTTP 接入（FastAPI / Starlette / Flask / Django）

Java 侧在 Web、网关、Feign、MQ 上全链路透传 trace，Python 服务通常挂在 chaos 网关之后，走的是 HTTP。
加上中间件即可接入同一条链路：

```python
# ASGI（FastAPI / Starlette）
from fastapi import FastAPI
from chaos_tracing import TraceContextASGIMiddleware

app = FastAPI()
app.add_middleware(TraceContextASGIMiddleware)
```

```python
# WSGI（Flask / Django）
from flask import Flask
from chaos_tracing import TraceContextWSGIMiddleware

app = Flask(__name__)
app.wsgi_app = TraceContextWSGIMiddleware(app.wsgi_app)
```

中间件会解析入站 `traceparent`（缺失时新建），在整个请求期间绑定到 `contextvars`，
并在响应上回写 `X-Trace-Id` / `X-Span-Id`。

调用下游时带上当前 trace：

```python
import httpx
from chaos_tracing import build_outgoing_headers

httpx.get("http://order-service/orders", headers=build_outgoing_headers())
```

## 版本对应

包版本与 Java 框架版本保持一致（当前 `1.0.0`），由 CI 的 `scripts/check-python-version.py` 校验。
