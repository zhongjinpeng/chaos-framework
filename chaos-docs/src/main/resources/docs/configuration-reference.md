# Configuration Reference

<!-- 本文件由 scripts/generate-configuration-reference.py 根据编译期配置元数据生成，请勿手工修改。 -->

全部 `chaos.*` 配置项的完整参考，由 `spring-configuration-metadata.json` 自动生成，与代码始终一致。
按场景挑选常用配置请先看 [Configuration Index](configuration-index.md) 和 [配置模板](templates/README.md)。

重新生成：

```bash
./mvnw -B -DskipTests compile
python3 scripts/generate-configuration-reference.py
```

## 目录

- [`chaos.audit`](#chaosaudit)（9 项）
- [`chaos.authorization`](#chaosauthorization)（54 项）
- [`chaos.diagnostics`](#chaosdiagnostics)（5 项）
- [`chaos.gateway`](#chaosgateway)（47 项）
- [`chaos.job`](#chaosjob)（4 项）
- [`chaos.mq`](#chaosmq)（17 项）
- [`chaos.mybatis`](#chaosmybatis)（16 项）
- [`chaos.nacos`](#chaosnacos)（4 项）
- [`chaos.production-safety`](#chaosproduction-safety)（5 项）
- [`chaos.redis`](#chaosredis)（1 项）
- [`chaos.security`](#chaossecurity)（21 项）
- [`chaos.service`](#chaosservice)（3 项）
- [`chaos.storage`](#chaosstorage)（9 项）
- [`chaos.tenant`](#chaostenant)（4 项）
- [`chaos.web`](#chaosweb)（18 项）

## chaos.audit

| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |
| --- | --- | --- | --- | --- | --- |
| `chaos.audit.async.enabled` | `Boolean` | `false` | 是否异步发布审计事件。 默认关闭，保持同步写入的既有行为。 配合 `chaos-audit-jdbc` 时强烈建议开启 ： 同步模式下每次登录、每次权限拒绝都是请求线程内的一次数据库写入，扫描器批量打未授权接口 会把审计写入变成可被外部触发的放大点。 |  | `chaos-autoconfigure` |
| `chaos.audit.async.queue-capacity` | `Integer` | `10000` | 待发布事件队列容量。 队列满时丢弃新事件并计入 `chaos.audit.events{outcome="dropped"`}，绝不阻塞业务线程： 队列满说明下游已经跟不上，此时阻塞业务只会把下游故障放大成全站故障。 合规要求零丢失时应关闭异步，或改用 MQ 实现。 |  | `chaos-autoconfigure` |
| `chaos.audit.enabled` | `Boolean` | `true` | 是否启用审计事件发布；关闭后注册 Noop 发布器。 |  | `chaos-autoconfigure` |
| `chaos.audit.jdbc.enabled` | `Boolean` | `false` | 是否启用 JDBC 审计持久化。 |  | `chaos-autoconfigure` |
| `chaos.audit.jdbc.fail-fast` | `Boolean` | `false` | 落库失败时是否直接抛出异常。 |  | `chaos-autoconfigure` |
| `chaos.audit.jdbc.independent-transaction` | `Boolean` | `true` | 是否在 REQUIRES_NEW 独立事务中写入审计。 默认 `true`：避免业务回滚时审计丢失，以及 PostgreSQL 上审计 SQL 失败导致外层事务进入 aborted 状态。 |  | `chaos-autoconfigure` |
| `chaos.audit.jdbc.mask-value` | `String` |  | 敏感属性脱敏占位值。 |  | `chaos-autoconfigure` |
| `chaos.audit.jdbc.sensitive-keywords` | `List<String>` |  | 按属性名包含匹配的敏感字段关键字（属性名去掉 `_ - .` 并转小写后匹配）。 `code`、`pin` 等短关键字按精确匹配内置处理，不要加入该列表，否则会误伤 `orderCode` 等字段。 |  | `chaos-autoconfigure` |
| `chaos.audit.jdbc.table-name` | `String` |  | 审计事件表名，支持 `table` 或 `schema.table` 格式。 |  | `chaos-autoconfigure` |

## chaos.authorization

| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |
| --- | --- | --- | --- | --- | --- |
| `chaos.authorization.access-token-ttl` | `Duration` | `2h` | access token 有效期。 |  | `chaos-authorization` |
| `chaos.authorization.captcha.enabled` | `Boolean` | `false` | 是否启用图形验证码端点；启用后需要 Redis 存储验证码（多实例间共享、消费后立即失效）。 |  | `chaos-authorization` |
| `chaos.authorization.captcha.height` | `Integer` | `44` | 验证码图片高度（像素），最小 36。 |  | `chaos-authorization` |
| `chaos.authorization.captcha.length` | `Integer` | `4` | 验证码字符数，最少 4 位。 |  | `chaos-authorization` |
| `chaos.authorization.captcha.path` | `String` | `/api/v1/auth/captcha` | 获取图形验证码的端点路径。 |  | `chaos-authorization` |
| `chaos.authorization.captcha.redis-key-prefix` | `String` | `chaos:authorization:captcha` | 验证码在 Redis 中的 key 前缀。 |  | `chaos-authorization` |
| `chaos.authorization.captcha.ttl` | `Duration` | `2m` | 验证码有效期。 |  | `chaos-authorization` |
| `chaos.authorization.captcha.width` | `Integer` | `128` | 验证码图片宽度（像素），最小 96。 |  | `chaos-authorization` |
| `chaos.authorization.client.id` | `String` | `chaos-client` | 默认内存客户端 ID。 |  | `chaos-authorization` |
| `chaos.authorization.client.redis-key-prefix` | `String` | `chaos:authorization:client` | Redis 客户端仓储的键前缀，仅 store-type=redis 时使用。 |  | `chaos-authorization` |
| `chaos.authorization.client.scopes` | `String[]` | `read, write` | 默认内存客户端 scopes。 |  | `chaos-authorization` |
| `chaos.authorization.client.secret` | `String` | `{noop}chaos-secret` | 默认内存客户端密钥。 |  | `chaos-authorization` |
| `chaos.authorization.client.store-type` | `ClientStoreType` | `memory` | 客户端仓储模式。 | `memory`, `jdbc`, `redis` | `chaos-authorization` |
| `chaos.authorization.consent.redis-key-prefix` | `String` | `chaos:authorization:consent` | 授权同意记录在 Redis 中的 key 前缀。 |  | `chaos-authorization` |
| `chaos.authorization.consent.store-type` | `ConsentStoreType` | `auto` | OAuth2 授权同意记录的存储方式；AUTO 按 token/client 存储方式自动选择。 | `auto`, `memory`, `jdbc`, `redis` | `chaos-authorization` |
| `chaos.authorization.enabled` | `Boolean` | `true` | 是否启用授权服务器自动装配。 |  | `chaos-authorization` |
| `chaos.authorization.grant.default-password-enabled` | `Boolean` | `true` | 是否启用默认用户名密码登录。 |  | `chaos-authorization` |
| `chaos.authorization.grant.default-sms-enabled` | `Boolean` | `true` | 是否启用默认手机号验证码登录。 |  | `chaos-authorization` |
| `chaos.authorization.issuer` | `String` | `http://localhost:9000` | OAuth2 issuer 地址。 |  | `chaos-authorization` |
| `chaos.authorization.jwk.key-id` | `String` |  | JWK key id。 |  | `chaos-authorization` |
| `chaos.authorization.jwk.previous-public-keys` | `List<PreviousKey>` |  | 轮换前的旧公钥，只用于发布到 JWKS 端点和验签，不用于签发。 密钥轮换时把旧公钥放在这里保留到旧 token 全部过期（至少一个 access token TTL）， 避免切换签名密钥的瞬间所有在途 token 验签失败。 |  | `chaos-authorization` |
| `chaos.authorization.jwk.private-key-location` | `String` |  | RSA 私钥 PEM 文件路径。 |  | `chaos-authorization` |
| `chaos.authorization.jwk.public-key-location` | `String` |  | RSA 公钥 PEM 文件路径。 |  | `chaos-authorization` |
| `chaos.authorization.kickout.enabled` | `Boolean` | `false` | 是否启用互踢。 |  | `chaos-authorization` |
| `chaos.authorization.kickout.max-local-sessions` | `Integer` | `10000` | 本地内存授权会话索引最多保留的会话数。 |  | `chaos-authorization` |
| `chaos.authorization.kickout.redis-key-prefix` | `String` | `chaos:authorization:kickout` | Redis 授权会话索引 key 前缀。 |  | `chaos-authorization` |
| `chaos.authorization.kickout.scope` | `Scope` | `client` | 互踢范围。 | `client`, `device`, `global` | `chaos-authorization` |
| `chaos.authorization.kickout.session-registry-type` | `SessionRegistryType` | `auto` | 授权会话索引类型。 | `auto`, `memory`, `redis` | `chaos-authorization` |
| `chaos.authorization.login-lock.enabled` | `Boolean` | `true` | 是否启用登录失败锁定。 |  | `chaos-authorization` |
| `chaos.authorization.login-lock.lock-duration` | `Duration` | `15m` | 锁定时长，同时也是失败计数的统计窗口。 |  | `chaos-authorization` |
| `chaos.authorization.login-lock.max-failures` | `Integer` | `5` | 锁定前允许的连续失败次数。 |  | `chaos-authorization` |
| `chaos.authorization.login-lock.max-local-entries` | `Integer` | `100000` | 内存实现最多保留的计数条目数。 |  | `chaos-authorization` |
| `chaos.authorization.login-lock.redis-key-prefix` | `String` | `chaos:authorization:login-failure` | Redis 存储 key 前缀；存在 StringRedisTemplate 时使用 Redis 实现，否则退化为单实例内存实现。 |  | `chaos-authorization` |
| `chaos.authorization.logout.enabled` | `Boolean` | `true` | 是否启用框架统一的退出登录端点。 |  | `chaos-authorization` |
| `chaos.authorization.logout.path` | `String` | `/api/v1/auth/logout` | 退出登录端点路径。 |  | `chaos-authorization` |
| `chaos.authorization.production-safety.allow-generated-jwk` | `Boolean` | `false` | 是否允许生产环境使用启动期临时生成的 JWK。 |  | `chaos-authorization` |
| `chaos.authorization.production-safety.allow-localhost-issuer` | `Boolean` | `false` | 是否允许生产环境使用 localhost issuer。 |  | `chaos-authorization` |
| `chaos.authorization.production-safety.allow-memory-authorization-store` | `Boolean` | `false` | 是否允许生产环境使用内存授权和授权同意仓储。 |  | `chaos-authorization` |
| `chaos.authorization.production-safety.allow-memory-client-store` | `Boolean` | `false` | 是否允许生产环境使用内存客户端仓储。 |  | `chaos-authorization` |
| `chaos.authorization.production-safety.allow-memory-session-registry` | `Boolean` | `false` | 是否允许生产环境在启用互踢时使用本地内存会话索引。 |  | `chaos-authorization` |
| `chaos.authorization.production-safety.allow-noop-audit-publisher` | `Boolean` | `false` | 是否允许生产环境使用空审计发布器或不提供审计发布器。 |  | `chaos-authorization` |
| `chaos.authorization.production-safety.allow-noop-client-secret` | `Boolean` | `false` | 是否允许生产环境使用 `{noop}` 客户端密钥。 |  | `chaos-authorization` |
| `chaos.authorization.production-safety.allow-noop-jwt-revocation-service` | `Boolean` | `false` | 是否允许生产环境使用空 JWT 撤销服务。 |  | `chaos-authorization` |
| `chaos.authorization.production-safety.allow-noop-kickout-service` | `Boolean` | `false` | 是否允许生产环境在启用互踢时使用空互踢服务。 |  | `chaos-authorization` |
| `chaos.authorization.production-safety.enabled` | `Boolean` | `true` | 是否启用生产安全检查。 |  | `chaos-authorization` |
| `chaos.authorization.production-safety.fail-fast` | `Boolean` | `true` | 发现违规项时是否阻止启动。 默认 `true`：默认 client secret、临时 JWK、localhost issuer、Noop 撤销服务等问题上线后 分别意味着公开凭据、重启全员掉线、注销不生效，必须在启动阶段暴露而不是淹没在 WARN 日志里。 迁移期可以用各项 `allow-*` 开关逐项放宽，或临时设置为 `false` 只告警。 |  | `chaos-authorization` |
| `chaos.authorization.production-safety.production-mode` | `Boolean` |  | 是否按生产模式执行安全检查；为空时根据 profiles 自动判断。 |  | `chaos-authorization` |
| `chaos.authorization.production-safety.profiles` | `String[]` | `prod, production, prd` | 识别为生产环境的 Spring profile 名称，默认 `prod,production,prd`。 必须与全局 `chaos.production-safety.profiles` 的默认值（`ProductionProfiles.DEFAULTS`）保持一致： 这里保留字面量是为了让 configuration processor 能生成默认值元数据，一致性由单元测试保证。 | `prod`, `production`, `prd` | `chaos-authorization` |
| `chaos.authorization.refresh-token-ttl` | `Duration` | `30d` | refresh token 有效期。 |  | `chaos-authorization` |
| `chaos.authorization.refresh-token.audit-replay-enabled` | `Boolean` | `true` | 检测到重放嫌疑时是否写入审计。 |  | `chaos-authorization` |
| `chaos.authorization.refresh-token.security-enabled` | `Boolean` | `true` | 是否启用 refresh token 安全增强。 |  | `chaos-authorization` |
| `chaos.authorization.reuse-refresh-tokens` | `Boolean` | `false` | 是否复用 refresh token。 |  | `chaos-authorization` |
| `chaos.authorization.token.redis-key-prefix` | `String` | `chaos:authorization` | Redis token 模式下使用的 key 前缀。 |  | `chaos-authorization` |
| `chaos.authorization.token.type` | `TokenType` | `jwt` | token 类型，JWT 为自包含 token，REDIS 为引用 token。 | `jwt`, `redis` | `chaos-authorization` |

## chaos.diagnostics

| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |
| --- | --- | --- | --- | --- | --- |
| `chaos.diagnostics.outbox-schema-check.enabled` | `Boolean` | `true` | 存在 JDBC outbox 仓储时是否在启动阶段检查 outbox 表是否存在；缺表时输出带建表脚本路径的 WARN，默认开启。 |  | `chaos-autoconfigure` |
| `chaos.diagnostics.startup-report.enabled` | `Boolean` | `true` | 是否在应用启动完成后输出 Chaos 启动报告（已启用功能、关键配置、诊断提示），默认开启。 |  | `chaos-autoconfigure` |
| `chaos.diagnostics.startup-report.identifiers` | `Map<String,String>` |  | 追加到启动报告抬头的自定义标识（如版本号、构建号、机房），按声明顺序渲染，默认为空。 中文等非「小写字母/数字/短横线」的 key 必须写成 `"[中文]"`，否则宽松绑定会剥掉这些字符 导致绑定失败。运行期才知道的值（hostname、Pod 名）改用 `ChaosStartupIdentifierContributor` Bean，同名 key 以配置为准。值按与其他配置相同的规则脱敏。详见 docs/diagnostics.md。 |  | `chaos-autoconfigure` |
| `chaos.diagnostics.startup-report.level` | `ReportLevel` | `info` | 启动报告的日志级别：INFO 或 DEBUG；设为 DEBUG 时只在开启 debug 日志后可见。 |  | `chaos-autoconfigure` |
| `chaos.diagnostics.web-stack-check.enabled` | `Boolean` | `true` | Servlet 应用的类路径中存在 chaos-gateway（只能运行在 WebFlux 上）时是否阻断启动，默认开启。 同时引入 chaos-web-service-starter 与 chaos-gateway-starter 时 Spring Boot 会选择 Servlet， 网关治理会被静默跳过；开启后启动即失败并说明应该移除哪个 starter。 |  | `chaos-autoconfigure` |

## chaos.gateway

| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |
| --- | --- | --- | --- | --- | --- |
| `chaos.gateway.access.admin-roles` | `List<String>` |  | 具备这些角色时直接放行。 |  | `chaos-gateway` |
| `chaos.gateway.access.combining-algorithm` | `PolicyCombiningAlgorithm` | `deny-overrides` | policies 之间的合并算法；配置策略与 RBAC 之间恒为拒绝优先。 | `deny-overrides`, `allow-overrides`, `first-applicable` | `chaos-gateway` |
| `chaos.gateway.access.enabled` | `Boolean` | `false` | 是否启用网关粗粒度鉴权；默认关闭，避免升级后原有路由突然 403。 |  | `chaos-gateway` |
| `chaos.gateway.access.policies` | `List<AccessPolicyProperties>` |  | ABAC 策略，属性引用可用 subject.*、resource.*、environment.*（含 clientIp、http.method、http.path）。 |  | `chaos-gateway` |
| `chaos.gateway.access.role-hierarchy` | `Map<String,List<String>>` |  | 角色继承关系，键继承值中的全部角色，例如 admin: [manager]。 |  | `chaos-gateway` |
| `chaos.gateway.access.rules` | `List<AccessRuleProperties>` |  | 路径与权限的映射规则，按顺序匹配，第一条命中的规则生效。 |  | `chaos-gateway` |
| `chaos.gateway.access.wildcard-permission-enabled` | `Boolean` | `true` | 是否允许 token 中的权限编码使用通配符，例如 order:* 覆盖 order:read。 |  | `chaos-gateway` |
| `chaos.gateway.auth-enabled` | `Boolean` | `true` | 是否启用网关鉴权。 |  | `chaos-gateway` |
| `chaos.gateway.blacklist` | `List<String>` |  | IP 黑名单。 |  | `chaos-gateway` |
| `chaos.gateway.fallback.enabled` | `Boolean` | `true` | 是否启用 Gateway 统一降级异常处理。 |  | `chaos-gateway` |
| `chaos.gateway.fallback.include-unhandled` | `Boolean` | `true` | 是否把未知异常也转换为统一 JSON。 |  | `chaos-gateway` |
| `chaos.gateway.gray-enabled` | `Boolean` | `true` | 是否启用灰度标签透传。 |  | `chaos-gateway` |
| `chaos.gateway.internal-headers` | `List<String>` |  | 入站请求中需要无条件剔除的内部身份请求头。 这些请求头只能由网关在认证成功后写入，客户端自带的值一律丢弃，防止伪造用户或租户身份。 | `X-User-Id`, `X-Tenant-Id` | `chaos-gateway` |
| `chaos.gateway.jwt.audiences` | `List<String>` |  | 允许的受众（aud）列表；配置后 token 的 aud 必须至少命中其中一个。 |  | `chaos-gateway` |
| `chaos.gateway.jwt.clock-skew` | `Duration` | `60s` | exp/nbf 校验允许的时钟偏差。 |  | `chaos-gateway` |
| `chaos.gateway.jwt.issuer-uri` | `String` |  | 期望的签发方（iss）；配置后签发方不一致的 token 会被拒绝。 |  | `chaos-gateway` |
| `chaos.gateway.jwt.jwk-set-uri` | `String` |  | JWK Set URI。 |  | `chaos-gateway` |
| `chaos.gateway.jwt.jws-algorithms` | `List<String>` |  | 允许的 JWS 签名算法，默认只接受 RS256，防止算法混淆。 | `RS256`, `RS384`, `RS512`, `ES256`, `PS256` | `chaos-gateway` |
| `chaos.gateway.jwt.revocation-check-enabled` | `Boolean` | `true` | 是否在网关检查 JWT 黑名单。 |  | `chaos-gateway` |
| `chaos.gateway.jwt.validation-enabled` | `Boolean` | `true` | 是否在网关校验 JWT。 |  | `chaos-gateway` |
| `chaos.gateway.nacos-routes.clear-on-empty` | `Boolean` | `false` | Nacos 配置为空时是否清空当前动态路由。 |  | `chaos-gateway-nacos` |
| `chaos.gateway.nacos-routes.data-id` | `String` | `chaos-gateway-routes.yaml` | 路由配置 dataId。 |  | `chaos-gateway-nacos` |
| `chaos.gateway.nacos-routes.enabled` | `Boolean` | `false` | 是否启用 Nacos 动态路由。 |  | `chaos-gateway-nacos` |
| `chaos.gateway.nacos-routes.fail-fast` | `Boolean` | `false` | 初始加载失败时是否阻止应用启动。 |  | `chaos-gateway-nacos` |
| `chaos.gateway.nacos-routes.group` | `String` | `DEFAULT_GROUP` | 路由配置 group。 |  | `chaos-gateway-nacos` |
| `chaos.gateway.nacos-routes.timeout-ms` | `Long` | `3000` | 初始拉取配置超时时间，单位毫秒。 |  | `chaos-gateway-nacos` |
| `chaos.gateway.opaque-token.cache-max-size` | `Integer` | `10000` | introspection 本地缓存最多保留的 token 数。 |  | `chaos-gateway` |
| `chaos.gateway.opaque-token.cache-ttl` | `Duration` | `30s` | introspection 成功结果的本地缓存时长；实际 TTL 取该值与 token exp 的较小值，0 表示不缓存。 缓存会让已撤销 token 在 TTL 内继续可用，属于性能与撤销实时性的折中。 |  | `chaos-gateway` |
| `chaos.gateway.opaque-token.client-id` | `String` |  | 调用 introspection 端点使用的 OAuth2 client_id。 |  | `chaos-gateway` |
| `chaos.gateway.opaque-token.client-secret` | `String` |  | 调用 introspection 端点使用的 OAuth2 client_secret。 |  | `chaos-gateway` |
| `chaos.gateway.opaque-token.introspection-uri` | `String` |  | introspection 端点地址。 |  | `chaos-gateway` |
| `chaos.gateway.opaque-token.timeout` | `Duration` | `3s` | 调用 introspection 端点的整体超时时间，避免授权服务器变慢拖垮网关。 |  | `chaos-gateway` |
| `chaos.gateway.rate-limit.default-permits-per-second` | `Integer` | `100` | 默认每秒许可数。 |  | `chaos-gateway` |
| `chaos.gateway.rate-limit.enabled` | `Boolean` | `false` | 是否启用网关全局限流。 |  | `chaos-gateway` |
| `chaos.gateway.rate-limit.fail-open` | `Boolean` | `true` | 限流器异常时是否放行。 |  | `chaos-gateway` |
| `chaos.gateway.rate-limit.key-types` | `List<KeyType>` |  | 默认限流 key 维度。 | `route`, `path`, `ip`, `user`, `tenant` | `chaos-gateway` |
| `chaos.gateway.rate-limit.max-local-keys` | `Integer` | `10000` | 默认内存限流器最多保留的 key 数。 |  | `chaos-gateway` |
| `chaos.gateway.rate-limit.rules` | `List<Rule>` |  | 路径级限流规则。 |  | `chaos-gateway` |
| `chaos.gateway.rate-limit.skip-paths` | `List<String>` |  | 跳过限流的路径（Ant 风格）。 与鉴权白名单相互独立：登录、验证码等接口通常在鉴权白名单中，但恰恰最需要限流。 |  | `chaos-gateway` |
| `chaos.gateway.reject-ambiguous-path` | `Boolean` | `true` | 是否拒绝包含路径穿越或歧义编码（`..`、`%2e`、`%2f`、`;`、反斜杠等）的请求。 白名单按解码后的路径匹配，而下游容器会再做一次规范化；两者不一致时可能绕过鉴权，因此默认直接返回 400。 |  | `chaos-gateway` |
| `chaos.gateway.request-timing-enabled` | `Boolean` | `true` | 是否输出请求耗时日志。 |  | `chaos-gateway` |
| `chaos.gateway.tenant.enabled` | `Boolean` | `true` | 是否启用租户状态校验。 |  | `chaos-gateway` |
| `chaos.gateway.tenant.fail-closed` | `Boolean` | `true` | 租户 ID 缺失、未知或不可访问时是否拒绝请求。 |  | `chaos-gateway` |
| `chaos.gateway.tenant.header-name` | `String` | `X-Tenant-Id` | 租户 ID 请求头名称。 |  | `chaos-gateway` |
| `chaos.gateway.token.type` | `TokenType` | `jwt` | token 校验类型；JWT 校验自包含 token，OPAQUE 通过授权服务器 introspection 校验引用 token。 | `jwt`, `opaque` | `chaos-gateway` |
| `chaos.gateway.trusted-proxies` | `List<String>` |  | 可信反向代理地址（精确 IP 或 CIDR）。 只有请求的直连地址命中该列表时才会解析 `X-Forwarded-For`/`X-Real-IP`； 默认为空，表示网关直接暴露在公网，任何转发头都不可信，避免客户端伪造 IP 绕过黑名单和限流。 | `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.1/32` | `chaos-gateway` |
| `chaos.gateway.whitelist` | `List<String>` |  | 鉴权白名单路径。 |  | `chaos-gateway` |

## chaos.job

| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |
| --- | --- | --- | --- | --- | --- |
| `chaos.job.enabled` | `Boolean` | `true` | 是否启用 chaos-job 自动装配。 |  | `chaos-autoconfigure` |
| `chaos.job.lock.key-prefix` | `String` | `chaos:job:` | 任务锁 key 前缀，最终 key 为 `key-prefix + jobName`（Redis 实现还会叠加 chaos.redis.key-prefix）。 |  | `chaos-autoconfigure` |
| `chaos.job.lock.lease-time` | `Duration` |  | 锁租约。为空时使用 Redisson watchdog 自动续期，任务执行多久锁就持有多久； 显式设置时必须大于任务最长执行时间，否则锁到期后其他实例可能并发执行。 |  | `chaos-autoconfigure` |
| `chaos.job.scheduling-enabled` | `Boolean` | `true` | 是否开启 Spring `@EnableScheduling`。默认开启以兼容旧版本；应用自行管理调度（如 XXL-JOB）时可关闭。 |  | `chaos-autoconfigure` |

## chaos.mq

| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |
| --- | --- | --- | --- | --- | --- |
| `chaos.mq.outbox.claim-timeout` | `Duration` | `5m` | SENDING 状态超过该时间后允许其他实例重新抢占派发，应明显大于 send-timeout。 |  | `chaos-autoconfigure` |
| `chaos.mq.outbox.cleanup.batch-size` | `Integer` | `500` | 每批删除条数。 |  | `chaos-autoconfigure` |
| `chaos.mq.outbox.cleanup.enabled` | `Boolean` | `true` | 是否定期删除已发送消息，避免 outbox 表无限增长。 |  | `chaos-autoconfigure` |
| `chaos.mq.outbox.cleanup.interval` | `Duration` | `1h` | 清理间隔。 |  | `chaos-autoconfigure` |
| `chaos.mq.outbox.cleanup.retention` | `Duration` | `7d` | 已发送消息保留时长。 |  | `chaos-autoconfigure` |
| `chaos.mq.outbox.dispatcher.batch-size` | `Integer` | `100` | 每轮抢占的消息条数。 |  | `chaos-autoconfigure` |
| `chaos.mq.outbox.dispatcher.enabled` | `Boolean` | `true` | 是否自动运行派发器。需要同时存在 outbox 仓储和真实 MessagePublisher（Kafka/RocketMQ/自定义）。 |  | `chaos-autoconfigure` |
| `chaos.mq.outbox.dispatcher.initial-delay` | `Duration` | `10s` | 应用启动后首次派发延迟。 |  | `chaos-autoconfigure` |
| `chaos.mq.outbox.dispatcher.interval` | `Duration` | `1s` | 两轮派发之间的间隔。 |  | `chaos-autoconfigure` |
| `chaos.mq.outbox.dispatcher.max-retry-times` | `Integer` | `10` | 最大重试次数，超过后进入死信。 |  | `chaos-autoconfigure` |
| `chaos.mq.outbox.dispatcher.retry-backoff` | `Duration` | `30s` | 固定重试间隔。 |  | `chaos-autoconfigure` |
| `chaos.mq.outbox.enabled` | `Boolean` | `true` | 是否启用 JDBC outbox 自动装配。 |  | `chaos-autoconfigure` |
| `chaos.mq.outbox.health.dead-letter-threshold` | `Long` | `100` | 死信条数告警阈值，超过则判定为不健康。 |  | `chaos-autoconfigure` |
| `chaos.mq.outbox.health.enabled` | `Boolean` | `true` | 是否注册 outbox 健康指示器。 |  | `chaos-autoconfigure` |
| `chaos.mq.outbox.health.pending-threshold` | `Long` | `10000` | 待派发条数告警阈值，超过则判定为不健康。 |  | `chaos-autoconfigure` |
| `chaos.mq.outbox.table-name` | `String` | `chaos_mq_outbox` | outbox 表名，只允许普通标识符（可带 schema 前缀）。 |  | `chaos-autoconfigure` |
| `chaos.mq.publisher.send-timeout` | `Duration` | `10s` | 等待 broker 确认的超时时间。超时视为发送失败，由 outbox 重试。 |  | `chaos-autoconfigure` |

## chaos.mybatis

| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |
| --- | --- | --- | --- | --- | --- |
| `chaos.mybatis.data-scope.empty-condition-behavior` | `EmptyConditionBehavior` | `deny` | 空数据权限条件处理策略。 | `deny`, `ignore` | `chaos-mybatis` |
| `chaos.mybatis.data-scope.enabled` | `Boolean` | `true` | 是否启用数据权限扩展点。 |  | `chaos-mybatis` |
| `chaos.mybatis.data-scope.ignore-table-prefixes` | `List<String>` |  | 忽略数据权限改写的表名前缀。 |  | `chaos-mybatis` |
| `chaos.mybatis.data-scope.ignore-tables` | `List<String>` |  | 忽略数据权限改写的精确表名。 |  | `chaos-mybatis` |
| `chaos.mybatis.db-type` | `DbType` | `mysql` | 分页插件数据库类型。 | `mysql`, `mariadb`, `postgre-sql`, `oracle`, `sql-server`, `h2`, `dm`, `kingbase-es`, `ocean-base` | `chaos-mybatis` |
| `chaos.mybatis.id-generator.datacenter-id` | `Long` |  | 数据中心 ID，取值范围 0-31；生产多节点建议显式指定且保持唯一组合。 |  | `chaos-mybatis` |
| `chaos.mybatis.id-generator.worker-id` | `Long` |  | 工作机器 ID，取值范围 0-31；生产多节点建议显式指定且保持唯一组合。 |  | `chaos-mybatis` |
| `chaos.mybatis.optimistic-lock.enabled` | `Boolean` | `true` | 是否注册乐观锁插件。只对带 `@Version` 字段的实体生效，可继承 `VersionedBaseEntity`。 |  | `chaos-mybatis` |
| `chaos.mybatis.pagination.max-limit` | `Long` | `500` | 单页最大条数。请求超过该值时按该值查询，防止 `size=100000000` 触发全表查询或批量导出； 设为 0 或负数表示不限制（不推荐）。 |  | `chaos-mybatis` |
| `chaos.mybatis.pagination.overflow` | `Boolean` | `false` | 页码超过总页数时是否回到第一页。 |  | `chaos-mybatis` |
| `chaos.mybatis.tenant.column` | `String` | `tenant_id` | 租户字段名。 |  | `chaos-mybatis` |
| `chaos.mybatis.tenant.enabled` | `Boolean` | `true` | 是否启用多租户 SQL 改写。 |  | `chaos-mybatis` |
| `chaos.mybatis.tenant.id-pattern` | `String` | `[A-Za-z0-9_.:@-]{1,128}` | 租户 ID 白名单正则。租户 ID 会拼入 SQL 字面量，不匹配时拒绝执行 SQL。 默认值与 chaos-core RequestContextSnapshot 的校验规则保持一致。 |  | `chaos-mybatis` |
| `chaos.mybatis.tenant.ignore-table-prefixes` | `List<String>` |  | 忽略多租户改写的表名前缀。 |  | `chaos-mybatis` |
| `chaos.mybatis.tenant.ignore-tables` | `List<String>` |  | 忽略多租户改写的精确表名。 |  | `chaos-mybatis` |
| `chaos.mybatis.tenant.missing-tenant-behavior` | `MissingTenantBehavior` | `deny` | 当前租户 ID 缺失时的处理策略。 | `deny`, `ignore` | `chaos-mybatis` |

## chaos.nacos

| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |
| --- | --- | --- | --- | --- | --- |
| `chaos.nacos.data-id-prefix` | `String` | `chaos` | 配置中心 dataId 前缀。 |  | `chaos-autoconfigure` |
| `chaos.nacos.enabled` | `Boolean` | `true` | 是否启用 chaos 对 Nacos 的约定配置。 |  | `chaos-autoconfigure` |
| `chaos.nacos.group` | `String` | `DEFAULT_GROUP` | 配置中心 group。 |  | `chaos-autoconfigure` |
| `chaos.nacos.metadata-prefix` | `String` | `chaos` | 服务实例元数据键名前缀。 |  | `chaos-autoconfigure` |

## chaos.production-safety

| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |
| --- | --- | --- | --- | --- | --- |
| `chaos.production-safety.allow-unsafe-defaults` | `Boolean` | `false` | 是否允许在生产模式下使用 InMemory / Noop 等危险默认实现（跳过检查）。 |  | `chaos-autoconfigure` |
| `chaos.production-safety.enabled` | `Boolean` | `true` | 是否启用生产安全检查。 |  | `chaos-autoconfigure` |
| `chaos.production-safety.fail-fast` | `Boolean` | `true` | 发现危险默认实现时是否阻断启动；false 时只输出告警，仅建议在灰度迁移期间使用。 |  | `chaos-autoconfigure` |
| `chaos.production-safety.production-mode` | `Boolean` |  | 显式声明是否为生产模式；不设置时按 chaos.production-safety.profiles 与激活的 profile 判断。 |  | `chaos-autoconfigure` |
| `chaos.production-safety.profiles` | `List<String>` | `prod, production, prd` | 视为生产环境的 profile 列表。 | `prod`, `production`, `prd` | `chaos-autoconfigure` |

## chaos.redis

| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |
| --- | --- | --- | --- | --- | --- |
| `chaos.redis.key-prefix` | `String` | `""` | 幂等、分布式锁、限流、缓存 key、布隆过滤器和延迟队列的统一 key 前缀。 多个服务共用同一个 Redis 时强烈建议设置为应用名（例如 `${spring.application.name`}）， 否则同名 key 会互相冲突。默认空字符串，与旧版本 key 保持兼容；修改前缀后旧 key 不再生效， 请在发布窗口内评估锁互斥和延迟队列存量数据。JWT 黑名单需要跨服务共享，不受该前缀影响。 |  | `chaos-autoconfigure` |

## chaos.security

| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |
| --- | --- | --- | --- | --- | --- |
| `chaos.security.access.admin-roles` | `List<String>` | `admin` | 具备这些角色时 RBAC 默认放行。 |  | `chaos-security` |
| `chaos.security.access.combining-algorithm` | `PolicyCombiningAlgorithm` | `deny-overrides` | policies 之间的合并算法；配置策略与 RBAC 之间恒为拒绝优先，DENY 策略始终可以否决 RBAC 放行。 | `deny-overrides`, `allow-overrides`, `first-applicable` | `chaos-security` |
| `chaos.security.access.policies` | `List<AccessPolicyProperties>` |  | ABAC 授权策略。 |  | `chaos-security` |
| `chaos.security.access.policy-cache-ttl` | `Duration` | `30s` | 远端策略的本地缓存时长，也是策略改动生效的最大延迟；拉取失败时继续使用上一份快照。 |  | `chaos-security` |
| `chaos.security.access.policy-redis-key` | `String` | `chaos:security:access:policies` | 远端策略在 Redis 中的 key，值是与 policies 结构一致的 JSON 数组。 |  | `chaos-security` |
| `chaos.security.access.policy-source` | `PolicySourceType` | `config` | 策略来源：config 只用本配置文件里的 policies；redis 额外从 Redis 读取策略，改完无需重启。 |  | `chaos-security` |
| `chaos.security.access.role-hierarchy` | `Map<String,List<String>>` |  | 角色继承关系，键继承值中的全部角色，例如 admin: [manager]。 |  | `chaos-security` |
| `chaos.security.access.wildcard-permission-enabled` | `Boolean` | `true` | 是否允许主体权限编码使用通配符，例如 order:* 覆盖 order:read。 |  | `chaos-security` |
| `chaos.security.enabled` | `Boolean` | `true` | 是否启用安全自动装配。 |  | `chaos-security` |
| `chaos.security.http-basic-enabled` | `Boolean` | `false` | 是否启用 HTTP Basic。 |  | `chaos-security` |
| `chaos.security.jwt.revocation-check-enabled` | `Boolean` | `true` | 是否启用 JWT 撤销检查。 |  | `chaos-security` |
| `chaos.security.jwt.revocation-fail-open` | `Boolean` | `false` | 撤销服务（如 Redis）异常时是否放行。 默认 `false`（fail-closed，返回 503）：已注销或被踢下线的 token 不会因为黑名单存储故障而重新生效。 对可用性要求高于撤销实时性的系统可以显式改为 `true`，此时异常只记录告警日志。 |  | `chaos-security` |
| `chaos.security.opaque-token.cache-max-size` | `Integer` | `10000` | introspection 本地缓存最大条目数，默认 10000。 |  | `chaos-security` |
| `chaos.security.opaque-token.cache-ttl` | `Duration` | `30s` | introspection 成功结果的本地缓存时长，默认 30 秒；设为 0 关闭缓存。 缓存能避免每个请求都同步调用授权服务器，代价是 token 撤销后最多在该时长内仍被接受。 |  | `chaos-security` |
| `chaos.security.opaque-token.client-id` | `String` |  | 调用 introspection 端点使用的 OAuth2 client_id。 |  | `chaos-security` |
| `chaos.security.opaque-token.client-secret` | `String` |  | 调用 introspection 端点使用的 OAuth2 client_secret。 |  | `chaos-security` |
| `chaos.security.opaque-token.connect-timeout` | `Duration` | `1s` | 连接授权服务器 introspection 端点的超时时间，默认 1 秒。 |  | `chaos-security` |
| `chaos.security.opaque-token.introspection-uri` | `String` |  | introspection 端点地址。 |  | `chaos-security` |
| `chaos.security.opaque-token.read-timeout` | `Duration` | `3s` | 读取 introspection 响应的超时时间，默认 3 秒；避免授权服务器变慢时长期占用业务线程。 |  | `chaos-security` |
| `chaos.security.permit-all` | `String[]` | `/actuator/health` | 允许匿名访问的路径列表。 | `/actuator/health`, `/actuator/health/**`, `/v3/api-docs/**`, `/swagger-ui/**` | `chaos-security` |
| `chaos.security.token.type` | `TokenType` | `jwt` | token 校验类型；JWT 校验自包含 token，OPAQUE 通过授权服务器 introspection 校验引用 token。 | `jwt`, `opaque` | `chaos-security` |

## chaos.service

| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |
| --- | --- | --- | --- | --- | --- |
| `chaos.service.context-propagation.micrometer-enabled` | `Boolean` | `false` | 是否把 chaos 上下文注册到 Micrometer Context Propagation（ContextRegistry）。 开启后 Reactor、CompletableFuture（配合 ContextExecutorService）等非 TaskExecutor 异步边界 也能传播 trace、租户和用户上下文。默认关闭：chaos 恢复快照时会整体恢复 MDC， 与 Micrometer Tracing 自带的 MDC 管理同时启用时需要先验证日志字段是否符合预期。 |  | `chaos-autoconfigure` |
| `chaos.service.retry.backoff-ms` | `Long` | `100` | 固定退避间隔，单位毫秒。 |  | `chaos-autoconfigure` |
| `chaos.service.retry.max-attempts` | `Integer` | `3` | 最大尝试次数（包含首次执行）。 |  | `chaos-autoconfigure` |

## chaos.storage

| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |
| --- | --- | --- | --- | --- | --- |
| `chaos.storage.allowed-content-types` | `List<String>` |  | 允许上传的 contentType 白名单，支持 `image/*` 通配；为空表示不限制。 |  | `chaos-autoconfigure` |
| `chaos.storage.max-object-size` | `DataSize` |  | 单个对象最大大小，例如 `20MB`；为空表示不限制。大小未知的流式上传会在读取超限时中断。 |  | `chaos-autoconfigure` |
| `chaos.storage.minio.access-key` | `String` |  | 访问密钥。 |  | `chaos-storage-minio` |
| `chaos.storage.minio.endpoint` | `String` |  | 服务端地址。 |  | `chaos-storage-minio` |
| `chaos.storage.minio.secret-key` | `String` |  | 访问密钥 Secret。 |  | `chaos-storage-minio` |
| `chaos.storage.oss.access-key-id` | `String` |  | 访问密钥。 |  | `chaos-storage-oss` |
| `chaos.storage.oss.access-key-secret` | `String` |  | 访问密钥 Secret。 |  | `chaos-storage-oss` |
| `chaos.storage.oss.endpoint` | `String` |  | OSS endpoint。 |  | `chaos-storage-oss` |
| `chaos.storage.provider` | `Provider` | `none` | 默认对象存储 provider；未设置时不自动注册 ObjectStorageClient。 | `none`, `minio`, `oss` | `chaos-autoconfigure` |

## chaos.tenant

| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |
| --- | --- | --- | --- | --- | --- |
| `chaos.tenant.enabled` | `Boolean` | `true` | 是否启用租户治理自动装配。 |  | `chaos-autoconfigure` |
| `chaos.tenant.fail-closed` | `Boolean` | `true` | 租户状态未知、缺失或不可访问时是否拒绝请求。 |  | `chaos-autoconfigure` |
| `chaos.tenant.servlet-filter.enabled` | `Boolean` | `false` | 是否在 Servlet 服务中启用租户状态校验过滤器。 默认关闭以保持兼容；启用前需要接入真实的 TenantStatusProvider，并确认租户 ID 来自认证结果。 |  | `chaos-autoconfigure` |
| `chaos.tenant.servlet-filter.exclude-paths` | `List<String>` |  | 不校验租户的路径（Ant 风格），例如 `/actuator/**`、登录接口。 |  | `chaos-autoconfigure` |

## chaos.web

| 配置项 | 类型 | 默认值 | 说明 | 可选值 | 所属 artifact |
| --- | --- | --- | --- | --- | --- |
| `chaos.web.forwarding.trust-identity-headers` | `Boolean` | `false` | 是否信任可信代理透传的 `X-User-Id`/`X-Tenant-Id` 身份请求头。 默认 `false`。开启后仍要求直连对端命中 `#trustedProxies`， 且上游网关必须先删除外部请求携带的同名请求头。 |  | `chaos-web` |
| `chaos.web.forwarding.trusted-proxies` | `List<String>` |  | 可信代理 IP 或 CIDR 列表，例如网关、Ingress 所在网段 `10.0.0.0/8`。 只有请求直连对端命中该列表时，才会解析 `X-Forwarded-For` 获取客户端 IP。 显式配置 `0.0.0.0/0` 表示信任所有来源，仅适用于网络层已隔离的内网服务。 | `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.1/32` | `chaos-web` |
| `chaos.web.i18n.default-locale` | `String` | `""` | 固定使用的语言标签，例如 `zh-CN`、`en`。 留空时按请求语言解析（Spring MVC 默认读取 `Accept-Language`，客户端未声明时取服务端默认语言）。 只对内部系统、或产品明确只提供单一语言时才需要固定。 | ``, `zh-CN`, `en` | `chaos-web` |
| `chaos.web.i18n.enabled` | `Boolean` | `true` | 是否启用错误文案国际化。 关闭后框架直接使用错误码默认消息和内置英文提示，不做任何 `MessageSource` 查找。 |  | `chaos-web` |
| `chaos.web.idempotent.max-local-keys` | `Integer` | `10000` | 默认内存幂等仓储最多保留的 key 数。 |  | `chaos-web` |
| `chaos.web.idempotent.replay.enabled` | `Boolean` | `false` | 是否回放首次响应。 默认关闭，保持"重复请求返回 409"的既有行为。开启后 `@Idempotent` 接口的重复请求 会直接返回首次执行的状态码与响应体，并带上 `Idempotency-Replayed: true` 响应头。 开启需要付出两项成本：写请求的响应体会被缓存一份用于采集，以及每个幂等请求多一次快照查询。 |  | `chaos-web` |
| `chaos.web.idempotent.replay.max-body-size` | `DataSize` | `64KB` | 单次响应体最大缓存大小，超过则不保存快照，该 key 的重复请求退回 409。 |  | `chaos-web` |
| `chaos.web.idempotent.replay.max-local-records` | `Integer` | `1000` | 内存响应快照存储最多保留的条数，仅在没有 Redis 实现时生效。 |  | `chaos-web` |
| `chaos.web.idempotent.replay.stored-headers` | `List<String>` | `Location` | 需要一并回放的响应头。 默认只有 `Location`：创建类接口的资源地址在响应头里，不回放就无法与首次响应等价。 其余响应头由本次请求重新生成，回放旧值会产生误导。 | `Location` | `chaos-web` |
| `chaos.web.idempotent.ttl` | `Duration` | `5m` | 幂等 key 保留时间。 |  | `chaos-web` |
| `chaos.web.rate-limit.default-permits-per-second` | `Integer` | `100` | 默认每秒许可数，未在 `@RateLimit` 指定时生效。 |  | `chaos-web` |
| `chaos.web.rate-limit.enabled` | `Boolean` | `true` | 是否启用限流拦截器。 |  | `chaos-web` |
| `chaos.web.rate-limit.max-local-keys` | `Integer` | `10000` | 默认内存限流器最多保留的 key 数。 |  | `chaos-web` |
| `chaos.web.request-timing-enabled` | `Boolean` | `true` | 是否输出请求耗时日志。 |  | `chaos-web` |
| `chaos.web.response-wrap-enabled` | `Boolean` | `true` | 是否启用统一响应包装。 |  | `chaos-web` |
| `chaos.web.trace-enabled` | `Boolean` | `true` | 是否启用 trace 过滤器。 |  | `chaos-web` |
| `chaos.web.xss-enabled` | `Boolean` | `false` | 是否启用基础 XSS 参数转义。 默认关闭：输入端转义会污染入库数据且无法覆盖 JSON 请求体，推荐在输出端按上下文编码。 仅建议在渲染服务端模板且无法改造输出编码的遗留系统中开启。 |  | `chaos-web` |
| `chaos.web.xss-exclude-paths` | `List<String>` |  | XSS 过滤排除路径（Ant 风格），匹配的路径不做 XSS 转义。 |  | `chaos-web` |
