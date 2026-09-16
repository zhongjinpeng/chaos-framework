# chaos-cloud（OpenFeign 透传）

> 仓库中不存在 `chaos-feign` 模块。拦截器实现 `TraceFeignRequestInterceptor` 位于 chaos-trace（`com.michael.chaos.trace.feign`，feign-core 为可选依赖），
> 由 `chaos-autoconfigure` 负责注册；
> Servlet 服务通过 `chaos-cloud-starter` 引入；响应式服务使用 `chaos-cloud-reactive-starter`（不带 OpenFeign）。

## 职责

OpenFeign Trace Header 自动透传，兼容 W3C Trace Context。

## 依赖方式

引入 `chaos-cloud-starter`。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-cloud-starter</artifactId>
</dependency>
```


## 配置

启用 @EnableFeignClients 或使用 starter 自动装配。

## 示例

```java
TraceFeignRequestInterceptor 会写入 X-Trace-Id、X-Span-Id、traceparent、tracestate 和 baggage。
```

## 扩展点

- 优先注册 Spring Bean 覆盖默认实现。
- 不在业务代码中依赖 autoconfigure 类。
- 不跨层调用 infra 实现，domain/application 只依赖接口。
- 通过 `TraceHeaders.outgoing()` 构建出站请求头，每次 Feign 调用都会创建新的 spanId。

## 注意事项

- 保持模块职责单一，避免把无关能力塞入当前模块。
- 新增公共 API 后同步更新本手册和 architecture.md。
- Feign 只负责请求头透传，不负责创建真实 OpenTelemetry span；真实 span 由 Micrometer Observation 或 Java agent 完成。
