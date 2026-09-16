# chaos-gateway

## 职责

统一鉴权、灰度、限流入口、黑名单、访问日志、Trace 透传。

## 依赖方式

引入 chaos-gateway-starter。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-gateway-starter</artifactId>
</dependency>
```

如果只复用过滤器或配置属性，可以直接依赖 `chaos-gateway`。

如需从 Nacos 动态加载 Gateway 路由，引入 `chaos-gateway-nacos-starter`：

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-gateway-nacos-starter</artifactId>
</dependency>
```

## 配置

```yaml
chaos:
  gateway:
    auth-enabled: true
    gray-enabled: true
    whitelist:
      - /actuator/health
      - /oauth2/**
    blacklist:
      - 10.0.0.10
      - 10.0.1.0/24
    # 网关前置代理（Nginx/SLB）地址，只有来自这些地址的 X-Forwarded-For/X-Real-IP 才被采信；默认为空
    trusted-proxies:
      - 10.0.0.0/8
    # 入站请求中无条件剔除的内部身份请求头，只能由网关在认证后写入
    internal-headers:
      - X-User-Id
      - X-Tenant-Id
    # 拒绝 ..、%2e、%2f、;、反斜杠等歧义路径（400）
    reject-ambiguous-path: true
    rate-limit:
      enabled: true
      fail-open: true
      default-permits-per-second: 100
      max-local-keys: 10000
      # 与鉴权白名单相互独立，登录接口虽然免鉴权但仍会被限流
      skip-paths:
        - /actuator/health
      key-types:
        - route
        - tenant
        - user
        - ip
      rules:
        - id: order-api
          path-pattern: /order/**
          permits-per-second: 200
          key-types:
            - route
            - tenant
            - user
    fallback:
      enabled: true
      include-unhandled: true
    tenant:
      enabled: true
      fail-closed: true
      header-name: X-Tenant-Id
    token:
      type: jwt
    jwt:
      validation-enabled: true
      revocation-check-enabled: true
      jwk-set-uri: http://auth-server:9000/oauth2/jwks
      # 期望的签发方与受众，生产环境必须配置
      issuer-uri: https://auth.example.com
      audiences:
        - chaos-client
      jws-algorithms:
        - RS256
      clock-skew: 60s
```

Redis/reference token 网关鉴权使用 opaque introspection：

```yaml
chaos:
  gateway:
    token:
      type: opaque
    opaque-token:
      introspection-uri: http://auth-server:9000/oauth2/introspect
      client-id: chaos-client
      client-secret: chaos-secret
      # 成功结果本地缓存时长（取与 token exp 的较小值），0 表示不缓存
      cache-ttl: 30s
      cache-max-size: 10000
      # introspection 调用整体超时
      timeout: 3s
```

Nacos 动态路由配置：

```yaml
chaos:
  gateway:
    nacos-routes:
      enabled: true
      data-id: chaos-gateway-routes.yaml
      group: DEFAULT_GROUP
      timeout-ms: 3000
      fail-fast: false
      clear-on-empty: false
```

对应 Nacos `dataId` 内容使用 Spring Cloud Gateway 原生 `RouteDefinition` 结构：

```yaml
routes:
  - id: order-service
    uri: lb://order-service
    order: 0
    predicates:
      - name: Path
        args:
          _genkey_0: /order/**
    filters:
      - name: StripPrefix
        args:
          _genkey_0: "1"
    metadata:
      owner: order
```

## 示例

```bash
curl -H 'Authorization: Bearer eyJ...' \
  -H 'X-Trace-Id: trace-1' \
  -H 'traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01' \
  -H 'X-Gray-Tag: beta' \
  http://gateway:8080/order/orders
```

鉴权失败和黑名单命中时返回统一 JSON：

```json
{"code":"401","message":"unauthorized","data":null,"traceId":"...","timestamp":1710000000000}
```

限流命中时返回 429：

```json
{"code":"429","message":"too many requests","data":null,"traceId":"...","timestamp":1710000000000}
```

下游连接失败、超时或未知服务异常会由统一降级处理器输出 JSON：

```json
{"code":"503","message":"service unavailable","data":null,"traceId":"...","timestamp":1710000000000}
```

租户被冻结、禁用、过期、删除或未知时，fail-closed 模式返回 403：

```json
{"code":"403","message":"forbidden","data":null,"traceId":"...","timestamp":1710000000000}
```

## 扩展点

- 优先注册 Spring Bean 覆盖默认实现。
- 不在业务代码中依赖 autoconfigure 类。
- 不跨层调用 infra 实现，domain/application 只依赖接口。
- 自定义同类型 `GlobalFilter` 可替换或扩展鉴权、灰度、黑名单和日志策略。
- 限流算法复用 chaos-core 的 `RateLimiter` SPI：默认 `InMemoryRateLimiter`（生产阻断），引入 `chaos-redis-starter` 后自动复用 `RedissonRateLimiter`；
  网关通过 `RateLimiterGatewayAdapter` 把阻塞式实现适配为 `GatewayRateLimiter`（非内存实现在 boundedElastic 上执行，避免阻塞事件循环）。
  需要原生响应式实现时直接覆盖 `GatewayRateLimiter` Bean。
- 客户端 IP 解析规则与 Servlet 服务一致，统一由 chaos-core 的 `ForwardedClientIpResolver` 实现；网关 `ClientIpResolver` 只负责读取请求并缓存结果。
- 网关只依赖 `chaos-security-api`（claim 名称、JWT 撤销 SPI），不再依赖 Servlet 版 chaos-security。
- 覆盖 `GatewayRateLimitKeyResolver` 可自定义 route、租户、用户、IP、路径等 key 生成策略。
- 覆盖 `TenantStatusProvider` 可接入租户中心、数据库或配置中心。
- 设置 `chaos.gateway.jwt.validation-enabled=false` 可只做 Authorization 头存在性校验，适合迁移期。
- 设置 `chaos.gateway.token.type=opaque` 可让 Gateway 通过授权服务器 introspection 校验 Redis/reference token。
- 引入 `chaos-gateway-nacos-starter` 并设置 `chaos.gateway.nacos-routes.enabled=true` 后，Gateway 从 Nacos `dataId` 读取动态路由。
- `GatewayTraceFilter` 优先解析 W3C `traceparent`，并向下游同时写入 legacy header 和 W3C header。

## 安全模型

### 过滤器执行顺序

| 顺序 | 过滤器 | 职责 |
|---|---|---|
| HIGHEST | `GatewayTraceFilter` | 补齐 traceId，非法 `X-Trace-Id` 重新生成 |
| +1 | `GatewayAccessLogFilter` | 访问日志（`X-Request-Id` 只记录合法格式） |
| +5 | `GatewayRequestSanitizeFilter` | 拒绝歧义路径、剔除内部身份头、按可信代理解析客户端 IP |
| +10 | `BlacklistFilter` | IP 黑名单 |
| +25 | `GrayTagFilter` | 灰度标签透传 |
| +30 | `JwtAuthenticationGatewayFilter` / `OpaqueTokenAuthenticationGatewayFilter` | 验签或 introspection，以 claim 重写身份头 |
| +35 | `TenantGatewayFilter` | 租户状态校验、租户一致性校验 |
| +40 | `GatewayRateLimitFilter` | 全局限流 |

### 身份请求头

下游服务信任 `X-User-Id`、`X-Tenant-Id`，因此这些请求头只能由网关写入：

1. `GatewayRequestSanitizeFilter` 无条件删除客户端传入的 `internal-headers`，原始值只保存在 exchange attribute 中，供租户过滤器识别"客户端想访问的租户"。
2. 鉴权过滤器验签成功后，先删除再按 token claim 写入身份头；claim 缺失时不写入。关闭 `jwt.validation-enabled` 时不透传任何身份头。
3. 白名单路径、`auth-enabled=false` 时下游收到的请求不包含任何身份头。

### 客户端 IP

- 未配置 `trusted-proxies` 时只使用 TCP 直连地址，所有 `X-Forwarded-For`/`X-Real-IP` 均被忽略。
- 直连地址命中可信代理时，把 `X-Forwarded-For` 与直连地址组成链路，从右往左跳过可信代理，第一个不可信地址即客户端 IP；最左侧的值可以被客户端任意伪造，不会被直接采用。
- 转发头中的值必须是 IP 字面量，主机名不会触发 DNS 解析。

### 路径匹配

所有过滤器通过 `GatewayWhitelistMatcher` 匹配路径。包含 `..`、`.`、`%2e`、`%2f`、`%5c`、`%25`、`;`、反斜杠的请求：

- `reject-ambiguous-path=true`（默认）时直接返回 400；
- 关闭后也永远不会命中白名单或 `skip-paths`（fail closed）。

### 租户一致性

已认证 token 中的租户是唯一可信来源。客户端通过 `tenant.header-name` 声明的租户与 token 租户不一致时返回 403；匿名访问（token 无租户）时才采信请求头中的租户，并在状态校验通过后由网关重新写入 `X-Tenant-Id`。

### Opaque token 错误分级

- token 无效（`BadOpaqueTokenException`）→ 401；
- 授权服务器超时、宕机等 → 503，避免客户端误以为登录失效而清空登录态。
- 开启缓存后 token 撤销最多延迟 `cache-ttl` 生效。

## 注意事项

- 生产环境必须配置 `jwk-set-uri`，让网关在入口处完成 JWT 签名校验；开启校验但没有 decoder 时默认返回 401。
- 同时应配置 `jwt.issuer-uri` 与 `jwt.audiences`；未配置时启动输出 WARN，同一 JWKS 为其他客户端签发的 token 也能通过网关。
- opaque token 模式必须配置 introspection URI 和客户端凭证；开启后 Gateway 会通过 introspection claim 透传 `X-User-Id` 与 `X-Tenant-Id`。
- 引入 Redis JWT 黑名单实现后，网关会检查已撤销 JWT，并在通过后向下游透传 `X-User-Id` 与 `X-Tenant-Id`。
- IP 黑名单支持精确 IP 和 CIDR；客户端 IP 解析规则见"安全模型 / 客户端 IP"，网关部署在代理之后时必须配置 `trusted-proxies`，否则黑名单和按 IP 限流看到的都是代理地址。
- Gateway 访问日志输出 method、path、status、traceId、ip、cost。
- Gateway 401/403 使用与 Web 层一致的响应结构，便于前端和调用方统一处理。
- Gateway 全局限流默认使用本地固定窗口，只适合单实例保护；生产集群建议覆盖为 Redis 或外部限流实现。
- 默认本地限流器有 `max-local-keys` 上限，超过上限后按 LRU 淘汰最久未访问的 key（被淘汰 key 计数重置），不会拒绝新 key，避免攻击者用大量随机 key 让全站 429。
- `rate-limit.fail-open=true` 时限流器异常会放行并输出 WARN 日志，优先保障可用性；设置为 `false` 时异常返回 429，优先保护后端。生产环境应对该告警日志配置监控。
- 限流跳过规则使用 `rate-limit.skip-paths`，不再复用鉴权白名单；升级后原本在白名单中的路径（例如登录接口）会开始参与限流。
- Gateway 租户状态校验在 JWT 鉴权后执行，以 token 租户为准，客户端声明的租户不一致时返回 403。
- `tenant.fail-closed=true` 时租户缺失、未知或不可访问都会返回 403；生产环境应保持开启。
- Gateway 统一降级处理器默认开启，会把连接失败、超时、未知主机等异常转换为 503 JSON；`fallback.include-unhandled=true` 时未知异常转换为 500 JSON。
- 如果业务需要完全接管 WebFlux 异常响应，可以注册自定义 `GatewayFallbackExceptionHandler` 或其他 `ErrorWebExceptionHandler`。
- Nacos 动态路由扩展不放入 `chaos-gateway` 核心模块，避免所有网关默认携带 Nacos SDK。
- Nacos 路由解析或监听刷新失败时默认保留上一次有效路由；`fail-fast=true` 只影响初始加载，适合生产环境阻止空路由或坏配置启动。
- `clear-on-empty=false` 时空配置不会清空当前路由，避免误删 Nacos 内容导致全量路由消失；确需以空配置清表时显式设置为 `true`。
- 路由配置会校验 `id`、`uri`、`predicates` 和重复 `id`；配置发布前建议在预发环境先验证。
- 白名单只放健康检查、认证端点和明确公开接口。
- 默认白名单只包含 `/actuator/health`；Prometheus 指标应通过内网、管理端口或显式白名单保护。
- 灰度标签通过请求头透传，后续可和 Nacos metadata 或路由权重联动。
- `tracestate` 和 `baggage` 会原样向下游透传；不要在网关向 baggage 写入敏感信息。
