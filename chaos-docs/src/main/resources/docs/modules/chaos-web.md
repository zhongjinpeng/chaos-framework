# chaos-web

## 职责

Servlet MVC 增强：统一返回、异常处理、校验、Trace、访问日志、限流、幂等、XSS（可选）。

仅在 Servlet Web 应用中生效（`@ConditionalOnWebApplication(type = SERVLET)`），WebFlux 网关不会注册这些组件。

## 依赖方式

引入 chaos-web-starter。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-web-starter</artifactId>
</dependency>
```

如果是业务应用，优先使用对应 starter；如果是 domain/application 纯接口依赖，才直接依赖功能模块。

## 配置

```yaml
chaos:
  web:
    trace-enabled: true
    response-wrap-enabled: true
    request-timing-enabled: true
    # 默认关闭：输入端转义会污染入库数据，且覆盖不到 JSON 请求体
    xss-enabled: false
    xss-exclude-paths: []
    forwarding:
      # 可信代理（网关、Ingress、SLB）IP/CIDR；为空时客户端 IP 一律取 remoteAddr
      trusted-proxies:
        - 10.0.0.0/8
      # 是否信任可信代理透传的 X-User-Id / X-Tenant-Id，默认 false
      trust-identity-headers: false
    rate-limit:
      enabled: true
      default-permits-per-second: 100
      max-local-keys: 10000
    idempotent:
      ttl: 5m
      max-local-keys: 10000
```

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `chaos.web.forwarding.trusted-proxies` | 空 | 只有直连对端命中该列表时才解析 `X-Forwarded-For`，并从右向左取第一个非可信地址 |
| `chaos.web.forwarding.trust-identity-headers` | `false` | 开启后，仅当直连对端命中 `trusted-proxies` 时才采纳 `X-User-Id`/`X-Tenant-Id` |
| `chaos.web.xss-enabled` | `false` | 1.0.3 起默认关闭 |

## 安全模型

### 身份请求头

`X-User-Id`、`X-Tenant-Id` 默认**不被信任**。旧版本 `TraceFilter` 会无条件读取这两个头写入 `RequestContext`，
攻击者伪造请求头即可冒充任意用户、跨租户读写数据（MyBatis 租户插件读取 `RequestContext`），并污染审计。

现在身份只来自：

1. 认证结果（`chaos-security` 的 `SecurityContextRequestFilter`）；或
2. 同时满足 `trust-identity-headers=true` 且直连对端命中 `trusted-proxies` 的上游透传。

启用第 2 种方式时，网关必须先删除外部请求携带的同名请求头（`chaos-gateway` 已默认处理）。

所有租户 ID、用户 ID 在进入 `RequestContextSnapshot` 时都会经过白名单 `[A-Za-z0-9_.:@-]{1,128}` 校验，
包含引号、空白、换行等字符的值会被视为缺失。

### 客户端 IP

`ClientIpResolver` 被 `TraceFilter`（MDC `ip`）和 `DefaultRateLimitKeyResolver` 共享。未配置可信代理时直接使用
`remoteAddr`。如果服务前面有反向代理，二选一：

- 配置 `chaos.web.forwarding.trusted-proxies`；或
- 使用 Spring Boot `server.forward-headers-strategy=native`，由容器改写 `remoteAddr`（此时不要再配置 `trusted-proxies`）。

## 统一响应与异常

- `ResultResponseBodyAdvice` 只在选中 Jackson 转换器时包装；`String`、`byte[]`、`Resource`、`StreamingResponseBody`
  以及 Spring Boot `ErrorController`、Actuator、springdoc 响应不包装。
- `GlobalExceptionHandler`：
  - Spring `ErrorResponse` 类异常（`ResponseStatusException`、`NoResourceFoundException`、`MaxUploadSizeExceededException` 等）
    沿用自身状态码，4xx 只记 debug 日志；
  - `AccessDeniedException` / `AuthenticationException` 原样抛出，交给 Spring Security 返回 401/403；
  - 5xx 的 `ChaosException` 记录 error 日志和堆栈，响应只返回错误码默认消息，不回显内部信息；
  - 405、415 使用与状态码一致的 `METHOD_NOT_ALLOWED`、`UNSUPPORTED_MEDIA_TYPE` 错误码；
  - 所有对外文案统一经 `ErrorMessageResolver` 解析，详见下文"错误文案国际化"。
- `JacksonCustomizer` 使用 `modulesToInstall` 追加 `JavaTimeModule`，不会覆盖 Boot 注册的模块、`@JsonComponent` 和自定义 Module Bean。

## 幂等

```java
@Idempotent                     // 默认要求 Idempotency-Key 请求头，缺失返回 400
@PostMapping("/orders")
public OrderDTO create(@RequestBody CreateOrderCommand command) { ... }

@Idempotent(requireKey = false) // 缺少 key 时跳过幂等校验
@PostMapping("/orders/draft")
public OrderDTO draft() { ... }

@Idempotent(replay = false)     // 开启全局回放时，本接口的重复请求仍返回 409
@PostMapping("/captcha")
public CaptchaDTO captcha() { ... }
```

- 幂等 key 格式：`idem:{method}:{path}:t={tenantId}:u={userId}:{key}`，不同用户、租户使用相同 key 不会互相阻塞。
- 不再使用 traceId 兜底（traceId 每个请求都不同，等于没有保护）。
- 请求头 key 最长 128 个可打印 ASCII 字符。
- 请求失败（异常被全局异常处理器转换、或响应状态码 >= 400）后释放占位，客户端可用同一个 key 重试；
  成功后保留占位直到 TTL 到期。

### 重复请求响应回放

默认重复请求返回 409。客户端因网络超时重发时拿到 409，并不能据此判断第一次是否成功，只能再调一次查询接口对账。
开启回放后，重复请求直接返回首次执行的状态码、响应体和 `Location` 头，并带上 `Idempotency-Replayed: true`：

```yaml
chaos:
  web:
    idempotent:
      replay:
        enabled: true       # 默认 false
        max-body-size: 64KB # 超出则不保存快照，重复请求退回 409
```

- 首次请求仍在执行中时返回 409（还没有快照可回放），稍后重试即可拿到回放结果。
- 开启后 `POST`/`PUT`/`PATCH`/`DELETE` 的响应体会被 `IdempotentResponseReplayFilter` 缓存一份用于采集，
  `GET`/`HEAD` 不受影响；每个 `@Idempotent` 请求另有一次快照查询开销。
- 集群部署必须配合 `chaos-redis-starter`：内存快照只存在于单个实例上，生产模式下会被 ProductionSafety 阻断启动。
- 完整边界见 [幂等能力文档](../capabilities/idempotency.md)。

## 错误文案国际化

```yaml
chaos:
  web:
    i18n:
      enabled: true      # 默认 true
      default-locale: "" # 留空 = 按 Accept-Language；填 zh-CN / en 则固定
```

- 查找顺序：应用 `MessageSource` → 框架内置资源包 → 错误码 `message()`。
- 内置错误码 key 为 `chaos.error.common.<枚举名转短横线>`，业务错误码默认 key 为 `chaos.error.<code>`。
- 业务在自己的 `messages.properties` 中定义同名 key 即可覆盖任意框架文案。
- 完整说明见 [错误文案国际化](../capabilities/i18n.md)。

## 限流

```java
@RateLimit(permitsPerSecond = 50)
public Map<String, Object> ping() {
    return Map.of("ok", true);
}
```

限流维度：方法签名 + 租户 + 用户（已认证）或客户端 IP（匿名）。

## Micrometer Tracing 集成

应用引入 `micrometer-tracing`（Brave 或 OpenTelemetry bridge）后：

- 自动注册 `CurrentSpanProvider`，`TraceFilter` 直接沿用 Micrometer 当前 span 的 traceId/spanId；
- `TraceFilter` 顺序调整为 `HIGHEST_PRECEDENCE + 2`，位于 Boot `ServerHttpObservationFilter` 之后；
- MDC `traceId`/`spanId`、响应头和 `Result.traceId` 与 APM 平台保持一致，不再出现两套 ID 互相覆盖。

## 示例

```java
@GetMapping("/orders")
public PageResult<OrderDTO> pageOrders(PageQuery query) {
    return new PageResult<>(records, total, query.pageNo(), query.pageSize());
}
```

Trace 请求头：

```bash
curl -H 'traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01' \
  -H 'baggage: tenant=acme,gray=beta' \
  http://localhost:8080/ping
```

## 扩展点

- 覆盖 `ClientIpResolver` 可接入自定义代理拓扑。
- 覆盖 `RateLimitKeyResolver` 可调整限流维度。
- 覆盖 `RateLimiter` 可接入 Redis、Sentinel、网关或其他限流组件。
- 覆盖 `IdempotentKeyGenerator` 可调整幂等 key 规则。
- 覆盖 `ErrorMessageResolver` 可把错误文案接到配置中心等外部来源。
- 注册 `CurrentSpanProvider` Bean 可接入其他追踪系统。

## 注意事项

- 未引入 Redis 时默认使用内存限流器和内存幂等仓储，仅适合单实例或本地开发；生产模式下默认阻断启动（见 [chaos-autoconfigure](chaos-autoconfigure.md)）。
- 内存限流器、内存幂等仓储达到 `max-local-keys` 后淘汰最久未使用 / 最早写入的 key，不再拒绝所有新 key。
- 请求结束时 `TraceFilter` 会调用 `ContextPropagation.clearAll()`，清理租户、数据权限、耗时统计等所有注册的线程上下文。
- 非法的 `X-Trace-Id`（非 `[A-Za-z0-9_-]{1,64}`）会被丢弃并重新生成，不会写回响应头。
- 响应头会写回 `X-Trace-Id`、`X-Span-Id`、`traceparent` 和 `tracestate`，便于调用方定位链路。

## 升级影响（1.0.3）

| 变更 | 影响 | 迁移方式 |
| --- | --- | --- |
| 身份请求头默认不信任 | 依赖网关透传 `X-User-Id`/`X-Tenant-Id` 且未引入认证的服务拿不到身份 | 引入 chaos-security，或配置 `forwarding.trusted-proxies` + `trust-identity-headers=true` |
| `@Idempotent` 缺少 key 返回 400 | 未传 `Idempotency-Key` 的客户端 | 客户端补充请求头，或设置 `requireKey = false` |
| XSS 过滤默认关闭 | 依赖输入端转义的旧页面 | 显式配置 `chaos.web.xss-enabled=true`，并尽快改为输出端编码 |
| 405/415 错误码变化 | 按错误码 `400` 判断的调用方 | 改为按 `405`/`415` 判断 |
