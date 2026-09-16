# chaos-framework 配置索引

## 使用原则

- 所有 framework 配置统一使用 `chaos.*` 前缀。
- 业务系统优先通过 starter 引入能力，避免直接依赖 autoconfigure。
- 生产环境建议保留 fail-closed / fail-fast 类配置，只有迁移期或压测隔离环境才临时放宽。
- 密钥、token、验证码、连接串等敏感配置必须通过配置中心或密钥系统注入，不写入代码仓库。
- 精确的属性说明以 IDE 中的配置元数据（`spring-configuration-metadata.json`）为准，本表只列关键项和风险。
- 全部配置项（名称、类型、默认值、说明、可选值）见自动生成的 [Configuration Reference](configuration-reference.md)；
  按场景复制即用的开发 / 生产配置见 [配置模板](templates/README.md)。

## 生产安全

| 前缀 | 模块 | 关键配置 | 默认值 | 风险提示 |
| --- | --- | --- | --- | --- |
| `chaos.production-safety` | chaos-autoconfigure（support 包；web/security/tenant/gateway/mybatis 等自动装配共用） | `enabled`、`production-mode`、`profiles`、`fail-fast`、`allow-unsafe-defaults` | `true`、空（按 profile 判断）、`prod,production,prd`、`true`、`false` | 生产模式下检测 `NoopJwtRevocationService`、`NoopTenantStatusProvider`、`NoopDataScopeProvider`、`InMemoryRateLimiter`、`InMemoryIdempotentRepository`、`InMemoryGatewayRateLimiter` 等兜底实现，**默认抛异常阻断启动**。`fail-fast=false` 降级为告警；`allow-unsafe-defaults=true` 仅用于迁移期 |
| `chaos.diagnostics` | chaos-autoconfigure（diagnostics 包） | `startup-report.enabled`、`startup-report.level`、`startup-report.identifiers`、`web-stack-check.enabled`、`outbox-schema-check.enabled` | `true`、`info`、空、`true`、`true` | 启动报告（已启用功能、未启用原因、诊断提示、自定义启动标识）与 `/actuator/chaos` 端点（需显式暴露）；Servlet 应用混入 chaos-gateway 时阻断启动；JDBC outbox 缺表时输出带建表脚本路径的 WARN。详见 [diagnostics.md](diagnostics.md) |
| `chaos.authorization.production-safety` | chaos-authorization | `enabled`、`production-mode`、`profiles`、`fail-fast`、`allow-*` | `true`、空、`prod,production,prd`（与全局 `chaos.production-safety.profiles` 一致）、`true`、全部 `false` | 检查 localhost issuer、`{noop}` client secret、memory client、临时生成的 JWK、内存授权仓储/会话索引、Noop JWT 撤销、Noop 互踢、空审计，**默认阻断启动**；逐项放行用 `allow-memory-client-store`、`allow-memory-authorization-store`、`allow-memory-session-registry`、`allow-generated-jwk`、`allow-noop-client-secret`、`allow-localhost-issuer`、`allow-noop-jwt-revocation-service`、`allow-noop-kickout-service`、`allow-noop-audit-publisher` |

## Web / Service / Tenant

| 前缀 | 模块 | Starter | 关键配置 | 默认值 | 风险提示 |
| --- | --- | --- | --- | --- | --- |
| `chaos.web` | chaos-web | chaos-web-starter | `response-wrap-enabled`、`trace-enabled`、`request-timing-enabled`、`xss-enabled`、`xss-exclude-paths` | `true`、`true`、`true`、**`false`**、空 | XSS 输入端转义默认关闭：会污染入库数据且覆盖不到 JSON 请求体，推荐输出端编码；关闭 trace 或耗时日志会降低排障能力 |
| `chaos.web.forwarding` | chaos-web | chaos-web-starter | `trusted-proxies`、`trust-identity-headers` | 空、`false` | 只有直连地址命中 `trusted-proxies`（IP/CIDR）时才解析 `X-Forwarded-For`；`X-User-Id`/`X-Tenant-Id` 默认**不信任**，开启 `trust-identity-headers` 前必须保证网关已剔除外部同名头 |
| `chaos.web.rate-limit` | chaos-web | chaos-web-starter | `enabled`、`default-permits-per-second`、`max-local-keys` | `true`、`100`、`10000` | 单机限流不适合多实例全局配额；本地 key 达到上限按 LRU 淘汰，不再拒绝新 key |
| `chaos.web.idempotent` | chaos-web | chaos-web-starter | `ttl`、`max-local-keys` | `5m`、`10000` | 请求失败（异常或 4xx/5xx）会释放幂等 key；TTL 过短可能无法覆盖慢请求重试窗口 |
| `chaos.web.idempotent.replay` | chaos-web | chaos-web-starter | `enabled`、`max-body-size`、`stored-headers`、`max-local-records` | **`false`**、`64KB`、`Location`、`1000` | 开启后重复请求回放首次响应而不是 409；代价是写请求响应体多缓存一份、每个幂等请求多一次快照查询；集群必须配 Redis，内存快照在生产模式下会被阻断启动 |
| `chaos.web.i18n` | chaos-web | chaos-web-starter | `enabled`、`default-locale` | `true`、空（按 `Accept-Language`） | 关闭后只用错误码默认消息；只提供单一语言的内部系统建议固定 `default-locale`，避免响应语言随客户端变化 |
| `chaos.audit.async` | chaos-autoconfigure | chaos-audit-starter | `enabled`、`queue-capacity` | **`false`**、`10000` | 配合 chaos-audit-jdbc 时强烈建议开启：同步模式下每次登录/权限拒绝都是请求线程内一次 DB 写入；队列满时丢弃并计指标，不阻塞业务 |
| `chaos.mq.outbox.health` | chaos-autoconfigure | chaos-mq-starter | `enabled`、`pending-threshold`、`dead-letter-threshold` | `true`、`10000`、`100` | 超过阈值时 /actuator/health 转 DOWN；仓储不支持统计时为 UNKNOWN 而非 UP |
| `chaos.service.retry` | chaos-autoconfigure | chaos-application-starter | `max-attempts`、`backoff-ms` | `3`、`100` | 业务异常不应重试 |
| `chaos.service.context-propagation` | chaos-autoconfigure | chaos-application-starter | `micrometer-enabled` | `false` | 开启后注册到 Micrometer ContextRegistry，Reactor/CompletableFuture 也能传播上下文；与 Micrometer Tracing 的 MDC 管理共存时需验证日志字段 |
| `chaos.tenant` | chaos-autoconfigure | chaos-tenant-starter | `enabled`、`fail-closed`、`header-name`、`default-isolation-mode` | `true`、`true`、`X-Tenant-Id`、`SHARED_SCHEMA` | 生产应替换真实 `TenantStatusProvider` |
| `chaos.tenant.servlet-filter` | chaos-autoconfigure | chaos-tenant-starter | `enabled`、`exclude-paths` | `false`、`/actuator/**,/error` | 开启后 Servlet 服务入口也按租户状态 fail-closed 校验，并负责清理 `TenantContext` |

## Security / Authorization

| 前缀 | 模块 | Starter | 关键配置 | 默认值 | 风险提示 |
| --- | --- | --- | --- | --- | --- |
| `chaos.security` | chaos-security | chaos-security-starter | `enabled`、`permit-all`、`http-basic-enabled`、`token.type`、`access.admin-roles` | `true`、健康检查、`false`、`JWT`、`admin` | 白名单只放明确公开接口；默认 RBAC 放行权限编码或 admin 角色；ABAC 策略 DENY 优先 |
| `chaos.security.jwt` | chaos-security | chaos-security-starter | `revocation-check-enabled`、`revocation-fail-open` | `true`、`false` | 已撤销 token 返回 401；撤销存储（Redis）自身异常时默认 fail-closed 返回 503；`revocation-fail-open=true` 优先可用性但注销/互踢可能失效 |
| `chaos.security.opaque-token` | chaos-security | chaos-security-starter | `introspection-uri`、`client-id`、`client-secret`、`cache-ttl`、`cache-max-size`、`connect-timeout`、`read-timeout` | 空、空、空、`30s`、`10000`、`1s`、`3s` | 缓存只保存成功结果，TTL 不超过 token exp，也是撤销生效的最大延迟窗口（撤销实时性要求高时设为 `0`）；超时避免授权服务器变慢占满业务线程 |
| `chaos.authorization` | chaos-authorization | chaos-authorization-starter | `issuer`、`access-token-ttl`、`refresh-token-ttl`、`reuse-refresh-tokens` | `http://localhost:9000`、`2h`、`30d`、`false` | 生产必须改 issuer；不建议复用 refresh token |
| `chaos.authorization.token` | chaos-authorization | chaos-authorization-starter | `type`、`redis-key-prefix` | `JWT`、`chaos:authorization` | REDIS token 模式需要 Redis 可用性保障 |
| `chaos.authorization.jwk` | chaos-authorization | chaos-authorization-starter | `public-key-location`、`private-key-location`、`key-id`、`previous-public-keys[].key-id`、`previous-public-keys[].public-key-location` | 空（随机生成）、空、随机 | 未配置密钥时每次启动随机生成 JWK，重启后所有 token 失效；轮换时把旧公钥放入 `previous-public-keys`，旧 token 过期前仍可验签 |
| `chaos.authorization.login-lock` | chaos-authorization | chaos-authorization-starter | `enabled`、`max-failures`、`lock-duration`、`redis-key-prefix`、`max-local-entries` | `true`、`5`、`15m`、`chaos:authorization:login-failure`、`100000` | 按“租户 + 用户名（忽略大小写）”统计 password grant 失败次数并锁定；多实例需 Redis |
| `chaos.authorization.refresh-token` | chaos-authorization | chaos-authorization-starter | `security-enabled`、`audit-replay-enabled` | `true`、`true` | 关闭后无法审计 refresh token 重放嫌疑 |
| `chaos.authorization.kickout` | chaos-authorization | chaos-authorization-starter | `enabled`、`scope`、`session-registry-type`、`max-local-sessions` | `false`、`CLIENT`、`AUTO`、`10000` | 多实例生产建议使用 Redis 索引；本地索引超限后拒绝新会话 |
| `chaos.authorization.client` | chaos-authorization | chaos-authorization-starter | `store-type`、`id`、`secret`、`redis-key-prefix` | `MEMORY`、`chaos-client`、`{noop}chaos-secret`、`chaos:authorization:client` | 默认 secret 仅供本地开发，生产模式会阻断启动 |
| `chaos.authorization.logout` | chaos-authorization | chaos-authorization-starter | `enabled`、`path` | `true`、`/api/v1/auth/logout` | 注销会同时撤销 access token 并清理会话索引 |
| `chaos.authorization.captcha` | chaos-authorization | chaos-authorization-starter | `enabled`、`path`、`ttl`、`length` | `false`、`/api/v1/auth/captcha`、`2m`、`4` | 需要 Redis |

## Gateway

| 前缀 | 模块 | Starter | 关键配置 | 默认值 | 风险提示 |
| --- | --- | --- | --- | --- | --- |
| `chaos.gateway` | chaos-gateway | chaos-gateway-starter | `auth-enabled`、`gray-enabled`、`request-timing-enabled`、`whitelist`、`blacklist` | `true`、`true`、`true`、`/actuator/health`、空 | 白名单扩大可能绕过鉴权；Prometheus 不应默认公开 |
| `chaos.gateway`（入口防护） | chaos-gateway | chaos-gateway-starter | `trusted-proxies`、`internal-headers`、`reject-ambiguous-path` | 空、`X-User-Id,X-Tenant-Id`、`true` | 只有直连地址命中可信代理才解析 `X-Forwarded-For`/`X-Real-IP`；`internal-headers` 中的入站头无条件剔除，认证成功后由网关重新写入；含 `..`、`;`、编码斜杠等歧义路径直接 400，防止白名单穿越 |
| `chaos.gateway.jwt` | chaos-gateway | chaos-gateway-starter | `validation-enabled`、`revocation-check-enabled`、`jwk-set-uri`、`issuer-uri`、`audiences`、`jws-algorithms`、`clock-skew` | `true`、`true`、空、空、空、`RS256`、`60s` | 开启校验但未配置 decoder 时默认 401；生产应配置 `issuer-uri` 与 `audiences`，否则同一 JWKS 签发给其他受众的 token 也会被接受 |
| `chaos.gateway.opaque-token` | chaos-gateway | chaos-gateway-starter | `introspection-uri`、`client-id`、`client-secret`、`cache-ttl`、`cache-max-size`、`timeout` | 空、空、空、`30s`、`10000`、`3s` | 缓存 TTL 即撤销生效的最大延迟窗口；超时避免授权服务器变慢拖垮网关 |
| `chaos.gateway.rate-limit` | chaos-gateway | chaos-gateway-starter | `enabled`、`fail-open`、`default-permits-per-second`、`max-local-keys`、`key-types`、`skip-paths`、`rules` | `false`、`true`、`100`、`10000`、`ROUTE,TENANT,USER,IP`、`/actuator/health` | 限流跳过路径与鉴权白名单相互独立，登录等白名单接口默认也会限流；fail-open 优先可用性、fail-closed 优先保护后端；本地 key 达到上限按 LRU 淘汰 |
| `chaos.gateway.fallback` | chaos-gateway | chaos-gateway-starter | `enabled`、`include-unhandled` | `true`、`true` | 完全接管异常响应时可覆盖处理器 |
| `chaos.gateway.tenant` | chaos-gateway | chaos-gateway-starter | `enabled`、`fail-closed`、`header-name` | `true`、`true`、`X-Tenant-Id` | 已认证请求以 JWT 租户为准，与请求头不一致时 403；fail-closed=false 可能放行未知租户 |
| `chaos.gateway.nacos-routes` | chaos-gateway-nacos | chaos-gateway-nacos-starter | `enabled`、`data-id`、`group`、`timeout-ms`、`fail-fast`、`clear-on-empty` | `false`、`chaos-gateway-routes.yaml`、`DEFAULT_GROUP`、`3000`、`false`、`false` | 默认保留上一次有效路由；生产建议发布前校验路由配置 |

## 数据访问 / 缓存 / 消息 / 任务 / 存储

| 前缀 | 模块 | Starter | 关键配置 | 默认值 | 风险提示 |
| --- | --- | --- | --- | --- | --- |
| `chaos.mybatis` | chaos-mybatis | chaos-mybatis-starter | `db-type` | `MYSQL` | 非 MySQL 数据库必须显式配置，避免分页方言和 SQL 字面量转义错误 |
| `chaos.mybatis.tenant` | chaos-mybatis | chaos-mybatis-starter | `enabled`、`column`、`missing-tenant-behavior`、`id-pattern`、`ignore-table-prefixes`、`ignore-tables` | `true`、`tenant_id`、`DENY`、`[A-Za-z0-9_.:@-]{1,128}`、`sys_`、空 | 租户 ID 不匹配 `id-pattern` 时拒绝执行 SQL（防注入）；ignore 配置过宽会造成租户隔离缺口 |
| `chaos.mybatis.data-scope` | chaos-mybatis | chaos-mybatis-starter | `enabled`、`empty-condition-behavior`、ignore 配置 | `true`、`DENY` | 不建议把空条件改成 IGNORE |
| `chaos.mybatis.pagination` | chaos-mybatis | chaos-mybatis-starter | `max-limit`、`overflow` | `500`、`false` | 单页上限防止 `size` 过大导致全表扫描或数据批量导出 |
| `chaos.mybatis.optimistic-lock` | chaos-mybatis | chaos-mybatis-starter | `enabled` | `true` | 需要实体字段标注 `@Version` 才生效 |
| `chaos.redis` | chaos-autoconfigure | chaos-redis-starter | `key-prefix` | 空 | 多服务共用 Redis 时应设置为应用名；修改前缀后旧 key 失效（锁、延迟队列存量数据需评估）；JWT 黑名单不受前缀影响 |
| `chaos.mq.publisher` | chaos-autoconfigure | chaos-mq-starter | `send-timeout` | `10s` | 同步等待 broker 确认；超时视为发送失败并进入 outbox 重试 |
| `chaos.mq.outbox` | chaos-mq-jdbc | chaos-mq-starter | `enabled`、`table-name`、`claim-timeout` | `true`、`chaos_mq_outbox`、`5m` | 多实例派发依赖 claim；SENDING 超时后允许重新抢占 |
| `chaos.mq.outbox.dispatcher` | chaos-autoconfigure | chaos-mq-starter | `enabled`、`initial-delay`、`interval`、`batch-size`、`max-retry-times`、`retry-backoff` | `true`、`10s`、`1s`、`100`、`10`、`30s` | 超过最大重试次数进入 DEAD_LETTER |
| `chaos.mq.outbox.cleanup` | chaos-autoconfigure | chaos-mq-starter | `enabled`、`retention`、`interval`、`batch-size` | `true`、`7d`、`1h`、`500` | 只清理 SENT 记录；保留期过短会影响问题追溯 |
| `chaos.job` | chaos-autoconfigure | chaos-job-starter | `enabled`、`scheduling-enabled` | `true`、`true` | 使用 XXL-JOB 等外部调度时关闭 `scheduling-enabled` |
| `chaos.job.lock` | chaos-autoconfigure | chaos-job-starter | `key-prefix`、`lease-time` | `chaos:job:`、空（watchdog 续期） | 多实例互斥依赖 `DistributedLock`；显式 `lease-time` 必须大于任务最长执行时间 |
| `chaos.audit` | chaos-audit | chaos-audit-starter | `enabled` | `true` | 关闭审计会降低安全追溯能力 |
| `chaos.audit.jdbc` | chaos-audit-jdbc | chaos-audit-jdbc-starter | `enabled`、`table-name`、`fail-fast`、`independent-transaction`、`mask-value`、`sensitive-keywords` | `false`、`chaos_audit_event`、`false`、`true` | 默认独立事务写审计，业务回滚不会丢失失败审计；fail-fast=true 会让审计库可用性影响业务链路 |
| `chaos.nacos` | chaos-autoconfigure | chaos-cloud-nacos-starter | `enabled`、`data-id-prefix`、`group`、`metadata-prefix` | `true`、`chaos`、`DEFAULT_GROUP`、`chaos` | 多环境应显式区分 group/namespace |
| `chaos.storage` | chaos-autoconfigure | chaos-storage-starter | `provider`、`max-object-size`、`allowed-content-types` | `NONE`、空（不限制）、空（不限制） | 必须显式设置 `oss` 或 `minio` 才会创建默认客户端；面向用户上传时应配置大小和类型白名单 |
| `chaos.storage.oss` | chaos-storage-oss | chaos-storage-starter | `endpoint`、`access-key-id`、`access-key-secret` | 空 | `provider=oss` 后必须配置完整密钥 |
| `chaos.storage.minio` | chaos-storage-minio | chaos-storage-starter | `endpoint`、`access-key`、`secret-key` | 空 | `provider=minio` 后必须配置完整密钥 |

## 配置校验

- 配置类统一使用 `@ConfigurationProperties` + `@Validated`。
- starter 需要提供 `spring-boot-starter-validation`，保证运行期 Bean Validation provider 可用。
- 承载 `@ConfigurationProperties` 类的模块（包括 `chaos-web`、`chaos-security`、`chaos-gateway` 等库模块，以及 `chaos-autoconfigure`）需要引入 optional 的 `spring-boot-configuration-processor`，输出 IDE 元数据；架构测试会检查。
- 对象存储配置按 `chaos.storage.provider` 条件启用；未显式选择 provider 时不会创建默认客户端。
- 通用生产安全检查可通过 `chaos.production-safety.production-mode=true` 显式启用，默认 fail-fast；只想告警时设置 `chaos.production-safety.fail-fast=false`。
- 授权服务器有独立的 `chaos.authorization.production-safety.production-mode`、`fail-fast` 和细粒度 `allow-*` 开关。
