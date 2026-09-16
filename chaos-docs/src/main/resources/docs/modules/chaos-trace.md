# chaos-trace

## 职责

TraceId/SpanId 创建、MDC 桥接、W3C Trace Context 兼容和出站 Header 传播。源码目录为 `chaos-observability/chaos-trace`，对外 artifactId 为 `chaos-trace`。

## 指标约定

原先独立的 `chaos-monitor` 已并入 `chaos-trace`：
包 `com.michael.chaos.monitor` 更名为 `com.michael.chaos.trace.monitor`。

| 类型 | 说明 |
| --- | --- |
| `MeterNames` | 框架统一指标名称常量 |
| `ChaosObservationFilter` | Micrometer Observation 过滤器，为观测数据补充 `framework=chaos` 低基数标签，可选补充 tenant 标签 |

`micrometer-observation` 在本模块中是 optional 依赖：只有引入 Micrometer（通常经 `chaos-application-starter` 或 Actuator）的应用才会加载
`trace.monitor` 包，纯 trace 传播场景不会被迫带上 Micrometer。Prometheus 端点需要显式暴露：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
```

## 依赖方式

通过 chaos-web-starter、chaos-cloud-starter（OpenFeign 透传）或 chaos-gateway-starter 引入。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-trace</artifactId>
</dependency>
```

如果是业务应用，优先使用对应 starter；如果是 domain/application 纯接口依赖，才直接依赖功能模块。

## 配置

chaos.web.trace-enabled=true。

`chaos-trace` 本身不强制依赖 OpenTelemetry SDK，`trace.monitor` 包提供 Micrometer Observation 过滤器，Web、Gateway、Feign 和 MQ 统一透传：

```text
X-Trace-Id
X-Span-Id
traceparent
tracestate
baggage
X-User-Id
X-Tenant-Id
```

W3C 头用于兼容 OpenTelemetry、Micrometer Observation 和 SkyWalking agent。Legacy `X-Trace-Id` / `X-Span-Id` 用于兼容企业内部旧系统。

## 示例

```java
TraceHeaders.outgoing().forEach(template::header);
```

Servlet 入口解析 W3C traceparent：

```java
TraceContext.startServer(
        request.getHeader(ChaosHeaders.TRACE_ID),
        request.getHeader(ChaosHeaders.TRACEPARENT),
        request.getHeader(ChaosHeaders.TRACESTATE),
        request.getHeader(ChaosHeaders.BAGGAGE),
        request.getHeader(ChaosHeaders.TENANT_ID),
        request.getHeader(ChaosHeaders.USER_ID),
        appName
);
```

`startServer` 只继承上游 TraceId、采样标志、tracestate 和 baggage，并始终为当前服务生成新的
SpanId。上游 SpanId 只作为 `traceparent` 中的父 Span 使用，不能复用为当前服务 SpanId。
Gateway 对内继续传播 W3C/legacy 链路头，对浏览器响应只输出单个 `X-Trace-Id`，不会暴露
`X-Span-Id`、`traceparent`、`tracestate` 或 `baggage`。

构建 baggage：

```java
String baggage = TraceBaggage.of(Map.of("tenant", "acme", "gray", "beta")).headerValue();
```

异步任务传播当前上下文：

```java
Runnable task = TraceContext.wrap(() -> {
    // 这里可以读取提交线程捕获到的 traceId、tenantId、userId 和 MDC。
    String traceId = TraceContext.traceId();
});
executor.execute(task);
```

临时恢复上下文并自动回滚：

```java
TraceContextSnapshot snapshot = TraceContext.capture();

try (TraceContext.Scope ignored = TraceContext.restore(snapshot)) {
    // 当前线程临时使用 snapshot 中的上下文。
}
```

MQ 消息发布时可合并当前 trace：

```java
MessageEnvelope<OrderCreated> envelope = new MessageEnvelope<>(
        messageId,
        "order.created",
        "created",
        payload,
        Map.of(),
        Instant.now()
).withTraceHeaders();
```

## 扩展点

- 优先注册 Spring Bean 覆盖默认实现。
- 不在业务代码中依赖 autoconfigure 类。
- 不跨层调用 infra 实现，domain/application 只依赖接口。
- 使用 OpenTelemetry Java agent 时，框架保留 W3C header，agent 可继续采集真实 span。
- 使用 SkyWalking Java agent 时，框架 MDC 字段继续用于日志检索，分布式 span 仍由 agent 采集。

## 注意事项

- 保持模块职责单一，避免把无关能力塞入当前模块。
- 新增公共 API 后同步更新本手册和 architecture.md。
- `baggage` 只能放低敏上下文，例如租户编码、灰度标签、区域标识；禁止放 token、手机号、身份证号、邮箱、密码和验证码。
- `traceparent` 不合法时会回退到 legacy `X-Trace-Id`，仍为空则自动生成新 traceId。
- legacy `X-Trace-Id`/`X-Span-Id` 只接受 `[A-Za-z0-9_-]{1,64}`，非法值丢弃并重新生成，防止响应头拆分和日志注入。
- 更高版本的 `traceparent`（如 `01-...-extra`）按 W3C 前向兼容规则取前 4 段解析；`ff` 版本和版本 `00` 的多余字段视为非法。
- `tracestate`、`baggage` 会移除控制字符，超过 8192 字符直接丢弃。
- `TraceContext.start` 写入 MDC 的租户、用户取自已校验的 `RequestContextSnapshot`，不再直接使用调用方原始入参。
- 引入 Micrometer Tracing 时可以调用 `TraceContext.adopt(W3cTraceContext, ...)` 沿用外部 span；chaos-web 已自动处理。
- `ContextPropagation.clearAll()` 清理 trace 时只移除框架 MDC 字段，保留业务自定义 MDC。
- 出站调用会创建新的 spanId，并保持相同 traceId。
- 在线程池、`@Async` 或手工异步任务中应使用 `TraceContext.wrap` 或 `chaos-application-starter` 自动注册的 `TaskDecorator`，避免依赖 `InheritableThreadLocal`。
