# Changelog

所有重要变更都记录在这里。版本格式遵循语义化版本：`MAJOR.MINOR.PATCH`。

## 1.0.0 — 2026-09-16

> 首个对外发布版本。此前的 `0.1.0-SNAPSHOT` / `1.0.1` / `1.0.2` 只在内部迭代，从未有项目引入，
> 因此不提供升级路径，也不保留旧坐标的 relocation POM。
>
> 下文按能力域记录 1.0.0 交付的内容。其中 **Security / Fixed 段描述的是内部迭代期间发现并修复的问题**——
> 对使用 1.0.0 的项目没有升级动作，保留是因为它们解释了当前默认值为什么是这样（例如为什么默认不信任
> `X-Forwarded-For`、为什么 `@Idempotent` 默认要求携带 key）。

### 模块结构

`chaos-dependencies` 是公共 BOM 与唯一版本来源（无 parent），根 `pom.xml`（`chaos-parent`）继承它、只负责插件与 profile。
所有 POM 版本为 `${revision}`，取值在 `.mvn/maven.config`。

按能力域分目录：`chaos-foundation/`（`chaos-core`、`chaos-domain`）、`chaos-observability/chaos-trace`、
`chaos-audit/`、`chaos-web`、`chaos-service`、`chaos-tenant`、`chaos-job`、
`chaos-security/`（`chaos-security-api`、`chaos-security`、`chaos-authorization`、`chaos-security-redis`）、
`chaos-gateway/`、`chaos-data/`（`chaos-mybatis`、`chaos-redis`）、`chaos-mq/`、`chaos-storage/`。

全部自动装配集中在单一的 `chaos-autoconfigure`（`com.michael.chaos.autoconfigure.<feature>`），
starter 只聚合依赖、不含 Java 代码，分场景 starter 与能力 starter 两层。
`chaos-boot-parent` 是业务应用推荐的 parent。

### Removed（1.0 死代码与冗余文件清理）

- **chaos-service**：删除 `SpringDomainEventPublisher`。它被 `TransactionalDomainEventPublisher` 完整取代
  （后者的无事务分支就是它的全部逻辑，另外还处理了提交后发布与监听器异常隔离），自动装配注册的一直是后者，
  全仓零引用。
- **docs**：删除 `security-remediation-2026-09.md`（155 行，内容与本文件 Security / Fixed 段重复）。
  其中「后续事项」里尚未被覆盖的一条（网关限流 fail-open 需配告警）已并入 `optimization-roadmap.md` 未决事项；
  验证快照（479 个测试）早已过期，不再保留。
- **build**：删除 `chaos-relocations/` 的 21 个 relocation POM 模块及其聚合器、文档与架构测试断言。
  它们唯一的作用是把内部迭代期的旧坐标重定向到当前坐标，而这些旧坐标从未被任何项目引入，没有服务对象。
- **docs**：删除 `migration-1.x-to-2.0.md`（354 行）。1.0.0 是首个对外版本，不存在需要迁移的旧版本；
  其中仍然有用的「接入时容易踩到的默认值」已提炼进本节上方的「值得注意的默认行为」，
  上线前检查项见 `docs/checklists/`。
- **docs**：`AGENTS.md` 从 8.1 KB 压到 3.6 KB。模块结构、构建命令、编码约定、依赖方向规则四大块原本在
  `CLAUDE.md` 与 `AGENTS.md` 各写一遍；两份副本会各自漂移，且漂移在有人照着过期那份做之前不可见。
  现以 `CLAUDE.md` 为唯一权威源，`AGENTS.md` 只保留它独有的测试与提交 / PR 约定。
- **repo**：删除 5 个空目录，其中 `chaos-examples/example-order-service/src/main/test/java/...` 是一棵 9 层、
  零文件的空树（源码根路径写错，POM 未引用，真正的测试在 `src/test/java`）；
  另外删除工作区中示例服务遗留的 `logs/` 运行时输出。

### Changed（1.0 文档整合）

- **docs**：删除 6 个 starter 独立文档页（`chaos-{audit,audit-jdbc,mq,security,storage,tenant}-starter.md`）。
  它们与对应库模块文档大面积重复（依赖方式、示例、注意事项逐字重合），19 个 starter 里只覆盖了 6 个，
  而且重复导致内容过期——`chaos-tenant-starter.md` 仍在记录本版本已删除的 `chaos.tenant.header-name`
  与 `chaos.tenant.default-isolation-mode`。其中真正独有的内容已迁入库模块文档：
  - MQ 自动装配条件表 → `chaos-mq.md`；outbox 边界说明 → `chaos-mq-jdbc.md`
  - 存储配置（provider / 上传限制）→ `chaos-storage.md`
  - 租户配置与 Servlet 租户校验 → `chaos-tenant.md`（剔除已删除的两个配置）
  - JDBC 审计使用步骤与完整配置 → `chaos-audit-jdbc.md`；`AUDIT_LOG` 输出约定 → `chaos-audit.md`
  - 安全侧两条装配顺序/claim 兜底说明 → `chaos-security.md`

  starter 选型仍由 `chaos-starters.md` 统一承载。
- **docs**：`optimization-roadmap.md` 从 367 行压到 78 行。原文档 26 条路线全部是"已完成"，
  外加 15 个逐项复述的"已完成项"章节，与 `CHANGELOG.md` 功能完全重叠且措辞已开始漂移。
  现在只保留目标、执行原则、交付记录表（保留"验收标准"一列作为同类能力的参照）
  和新增的**未决事项**清单（工程门禁 5 项 + 能力缺口 6 项，含各自现状与备注）。

### Added（1.0 可观测性与工程治理）

- **metrics**：新增治理指标。框架此前替业务做限流、幂等、租户准入、权限校验、token 撤销、消息投递等决策，
  但这些决策只写日志，线上无法回答"下单成功率下跌究竟被谁挡了"。
  - `chaos-core` 新增 `ChaosMetrics`（上报端口）、`ChaosMeterNames`、`NoopChaosMetrics`；
    `chaos-trace` 新增 `MicrometerChaosMetrics`；`chaos-autoconfigure` 新增 `ChaosMetricsAutoConfiguration`
    （排在各能力自动装配之前，没有 `MeterRegistry` 时注册空实现，使调用点无需判空）。
  - 埋点覆盖 12 个指标：限流拒绝/失败、幂等拒绝/回放/快照跳过、租户拒绝、权限拒绝、认证失败、
    token 撤销命中、outbox 派发结果与待派发深度、审计投递结果。完整清单见
    [治理指标与健康检查](chaos-docs/src/main/resources/docs/capabilities/metrics.md)。
  - 标签只允许低基数枚举值；网关认证失败的 `reason` 用调用点给出的固定枚举，异常原文只进审计。
- **health**：`OutboxMessageRepository` 新增 `countByStatus`（JDBC 实现已提供），
  `chaos-mq` 新增 `OutboxHealth` / `OutboxHealthIndicator`，出现在 `/actuator/health` 的 `chaosMqOutbox` 下。
  派发器卡住时消息不断堆积，但此前健康检查依然 UP，滚动发布和扩缩容都会照常放行。
  仓储不支持统计时状态为 `UNKNOWN` 而非 `UP`。
- **audit**：新增 `AsyncAuditEventPublisher`（`chaos.audit.async.enabled`，默认关闭）。
  同步模式下每次登录、每次权限拒绝都是请求线程内的一次数据库写入，扫描器批量打未授权接口
  会把审计写入变成可被外部触发的放大点。有界队列满时丢弃并计入
  `chaos.audit.events{outcome="dropped"}`，绝不阻塞业务线程。
- **i18n**：网关错误响应接入国际化。`chaos-gateway` 是响应式栈、不依赖 chaos-web，
  因此把 `chaos.error.common.*` 文案资源包下沉到 `chaos-core`，两侧共用；网关直接读 `ResourceBundle`，
  不引入 `MessageSource` 基础设施。
- **build**：`chaos-release` profile 新增 CycloneDX SBOM；根 POM enforcer 新增 `bannedDependencies`；
  CI 新增 `dependency-review-action`（PR 门禁，high 及以上阻断）。CodeQL 只扫描本仓库源码，
  不看依赖的已知漏洞，而框架给下游钉死了传递依赖版本。
- **chaos-python**：新增 HTTP 链路透传。此前只有 gRPC server 拦截器，而 Python 服务通常挂在 chaos 网关
  之后走 HTTP，拿不到入站 traceparent。新增 `TraceContextASGIMiddleware`（FastAPI/Starlette）、
  `TraceContextWSGIMiddleware`（Flask/Django）和 `build_outgoing_headers()`；包版本对齐框架版本，
  由 `scripts/check-python-version.py` 在 CI 校验；CI 新增 ruff 与 mypy strict。

### Changed（1.0 可观测性与工程治理）

- **testing（修复静默失效）**：`OptionalDependencyIsolationTest` 在 `mvn verify` 下只跑 41 个用例并以
  PreconditionViolation 失败，而 `mvn test` 下跑 88 个。原因是类路径解析只认本地仓库布局，
  `verify` 在 package 之后把 reactor 依赖解析成 `<module>/target/*.jar`，导致所有 chaos 模块的
  `pom` 为 null、`chaosLibraryScenarios()` 退化为 0 个场景；同一次运行里
  `shouldStartWithoutAnyFeatureLibrary` 也退化成"只移除三方 jar"却依然通过。
  现在补上 reactor jar 布局解析，并新增 `scenarioSourcesShouldNotSilentlyDegrade` 把场景数量本身变成断言。
  `verify` 阶段由 41 个用例 + 1 error 变为 89 个全过。
- **testing**：`LayerBoundaryArchitectureTest` 中三个"源码包含子串"的网关测试被替换。
  `gatewayErrorsShouldUseJsonResponseWriter` 只要文件文本里出现 `"traceId"` 四个字符就通过，
  现改为 `gatewayErrorResponseShouldMatchResultShape`，解析 `Result` 记录组件与网关 JSON 模板字段逐一比对；
  `gatewayShouldProvideRateLimitFilter`、`gatewayShouldProvideFallbackExceptionHandler`
  （断言"文件存在"加"源码里出现过 RateLimit / Fallback"，一句注释即可通过）直接删除，
  相应约束由新增的 `GatewayErrorResponseWriterTest` 等真实请求断言覆盖。
- **performance**：`TokenIntrospectionCache` 改为每线程复用 `MessageDigest`。
  `MessageDigest.getInstance` 每次都走 JCA provider 查找，而该方法位于网关和资源服务器的每请求路径上。

### Removed（1.0 废弃 API 清理）

大版本是清理废弃 API 的唯一窗口，以下自 `0.1.0` / `1.0.3` 标记废弃的成员全部移除：

| 移除项 | 替代 |
| --- | --- |
| `RequestContext.traceId()`（`forRemoval = true`，自 0.1.0） | `TraceContext.traceId()` |
| `com.michael.chaos.audit.jdbc.AuditAttributeSanitizer` / `DefaultAuditAttributeSanitizer` | `com.michael.chaos.audit.*` 下的同名类型 |
| `IdempotentKeyContext` 的 5 参构造器 | 携带 `tenantId` / `userId` 的完整构造器 |
| `ChaosLogoutController(OAuth2AuthorizationService)` 单参构造器 | 包含撤销服务的完整构造器 |
| `chaos.tenant.header-name`、`chaos.tenant.default-isolation-mode` | 网关用 `chaos.gateway.tenant.header-name`；隔离模式由 `TenantStatusProvider` 返回 |
| `com.michael.chaos.trace.monitor.MeterNames` | `com.michael.chaos.core.metrics.ChaosMeterNames`（旧类声明的两个指标从未被任何代码发射） |

### Added（1.0 易用性：幂等响应回放与错误文案国际化）

- **idempotency**：`@Idempotent` 支持回放首次响应。此前重复请求一律返回 409，客户端因网络超时重发时拿不到第一次的结果，
  无法判断究竟成功没有，只能再调一次查询接口对账。开启 `chaos.web.idempotent.replay.enabled=true` 后，
  重复请求直接返回首次执行的状态码、响应体和 `Location` 头，并带上 `Idempotency-Replayed: true` 响应头。
  - `chaos-core` 新增 `IdempotentRecord`、`IdempotentRecordStore`、`IdempotentRecordCodec`（带版本号的文本线格式，跨实例共用）
    与仅供开发使用的 `InMemoryIdempotentRecordStore`。
  - `chaos-web` 新增 `IdempotentResponseReplayFilter` 负责采集响应体（`HandlerInterceptor` 拿不到已写出的响应体，
    必须在过滤器层用 `ContentCachingResponseWrapper` 缓存）；`@Idempotent(replay = false)` 可按接口关闭。
  - `chaos-redis` 新增 `RedissonIdempotentRecordStore`，快照 key 追加 `:response` 后缀与占位 key 分离，
    固定使用 `StringCodec` 以免业务替换全局编解码器后滚动发布期间格式不一致。
  - 幂等判定从两态变为三态：已完成 → 回放；执行中 → 409；已失败 → 释放占位后重新执行。
  - 默认关闭。开启的代价是写请求响应体多缓存一份（受 `max-body-size` 约束，默认 64KB）以及每个幂等请求多一次快照查询。
    集群部署必须配合 `chaos-redis-starter`，生产模式下 `InMemoryIdempotentRecordStore` 会被 ProductionSafety 阻断启动。
- **i18n**：错误文案接入 `MessageSource`。`chaos-web` 新增 `ErrorMessageResolver` / `MessageSourceErrorMessageResolver` /
  `ChaosMessageKeys` 与内置资源包（英文根包 + `zh_CN` / `zh`）；`chaos-core` 的 `ErrorCode` 新增 default 方法 `messageKey()`。
  查找顺序为应用 `MessageSource` → 框架资源包 → 错误码 `message()`，业务定义同名 key 即可覆盖任意框架文案。
  `BizException` 携带的消息先当作 key 查找，查不到按字面量返回，传字面量的老代码不受影响。

### Changed（1.0 易用性：幂等响应回放与错误文案国际化）

- **i18n（行为变化）**：`GlobalExceptionHandler` 中原本硬编码中文的参数校验提示（缺少参数、缺少请求头、类型不匹配、
  请求体无法解析、不支持的方法 / Content-Type）改为从资源包解析。此前错误码默认消息是英文、这些提示是中文，
  同一个服务的错误响应中英混杂；现在语言统一，并按 `Accept-Language` 切换。
  **默认语言随请求语言变化**（客户端未声明时取服务端默认语言），需要固定为中文请配置
  `chaos.web.i18n.default-locale=zh-CN`，完全关闭国际化用 `chaos.web.i18n.enabled=false`。
- **i18n**：`CommonErrorCode` 覆盖 `messageKey()` 使用 `chaos.error.common.<枚举名转短横线>` 命名空间，
  与业务错误码默认的 `chaos.error.<code>` 分开，避免业务枚举中的 `404`、`500` 等数字码被框架文案顶掉。

### Added（1.0 易用性：该依赖什么、该配什么）

- **starters**：新增场景 starter——`chaos-web-service-starter`（Servlet 业务服务：web + security + tenant + audit + application）、
  `chaos-auth-server-starter`（授权服务器：authorization + Actuator + Prometheus）；`chaos-gateway-starter` 补齐 Actuator，成为完整网关场景。
  一个应用选一个场景 starter，再按需追加 mybatis / redis / mq / job / storage / cloud 等能力 starter。
  新增依赖方向规则 10：场景 starter 只能聚合能力 starter，能力 starter 不得依赖场景 starter。
- **build**：新增 `chaos-boot-parent`，业务应用推荐 parent（继承 `chaos-dependencies`；Java 21、`-parameters`、资源占位符过滤、
  surefire / failsafe、spring-boot `repackage` 预置），不携带框架自身的发布治理插件。
- **config**：`additional-spring-configuration-metadata.json` 为 token 类型、存储类型、租户 / 数据权限缺失策略、限流维度、JWS 算法、
  可信代理网段、生产 profile 等提供 IDE 可选值提示；补齐 `chaos.production-safety.*`、`chaos.audit.enabled` 与授权服务器验证码 / 授权同意配置的元数据说明。
- **docs**：新增自动生成的 `configuration-reference.md`（`scripts/generate-configuration-reference.py`，CI 校验是否最新）；
  新增 `docs/templates` 场景配置模板（web-service / gateway / auth-server 的开发与生产模板，mybatis / redis / mq / storage 能力模板），
  由 `ConfigurationTemplatesTest` 校验配置项存在且可绑定；新增 `modules/chaos-boot-parent.md`，重写 `modules/chaos-starters.md` 为选型指南。

### Added（1.0 易用性：脚手架与上手文档）

- **archetypes**：新增 `chaos-archetypes`（`chaos-archetype-web-service`、`chaos-archetype-gateway`、`chaos-archetype-auth-server`），
  生成以 `chaos-boot-parent` 为 parent、带开发/生产配置、离线即可通过测试的项目；web-service 含 DDD 分层示例。
  配置与 `docs/templates` 的一致性、Velocity 渲染陷阱由 `ArchetypeArchitectureTest` 校验。
- **ci**：新增 `scripts/verify-archetypes.sh` 与 CI `archetypes` job：install 后用本地 archetype 生成三个项目并执行 `mvn verify`。
- **docs**：新增 [5 分钟上手](chaos-docs/src/main/resources/docs/getting-started.md)、三份生产检查清单（`docs/checklists/`）、
  `modules/chaos-archetypes.md`；文档首页改为“我要…”场景决策表；仓库根目录新增 `README.md`。

### Fixed（1.0 易用性：脚手架与上手文档）

- **cloud-nacos**：`ChaosCloudNacosAutoConfiguration` 此前没有任何类路径条件，未引入 Nacos 的网关、授权服务器也会注册约定 Bean，
  启动报告误报 cloud-nacos 已启用。现在要求类路径存在 nacos-client，且 `spring.cloud.nacos.discovery.enabled` 与
  `spring.cloud.nacos.config.enabled` 未被同时关闭。

### Added（1.0 易用性：启动诊断）

- **diagnostics**：启动完成后输出 Chaos 启动报告（已启用功能与关键配置、未启用原因分类、诊断提示），配置值脱敏；
  `chaos.diagnostics.startup-report.enabled` / `level` 控制。新增只读 actuator 端点 `/actuator/chaos`（默认不暴露）。
- **diagnostics**：新增 `ChaosDiagnosticRule` SPI 与内置规则（Redis key 前缀、开发用实现提前提示、网关 issuer/audiences、可信代理、
  生产安全被放宽、网关应用混入 chaos-web）。
- **core**：新增 `ChaosDiagnostic` / `ChaosDiagnosticException`（继承 `IllegalStateException`），框架配置错误统一输出"问题 / 原因 / 怎么修"：
  生产安全检查（列出违规 Bean 名称）、授权服务器生产安全检查、opaque introspection 缺少凭据、多租户缺少租户或租户 ID 非法（不回显租户值）。
- **gateway**：开启 JWT 鉴权却没有 JWT 解码器时启动即失败（此前运行期对所有请求返回 401）。
- **diagnostics**：Servlet 应用类路径中存在 chaos-gateway 时启动即失败并说明应移除哪个 starter（`chaos.diagnostics.web-stack-check.enabled`）。
- **mq**：JDBC outbox 缺表时启动输出带方言建表脚本路径的 WARN（`chaos.diagnostics.outbox-schema-check.enabled`）。
- **autoconfigure**：`chaos-autoconfigure` 新增对 `chaos-core` 的 compile 依赖（纯 Java、无三方依赖）。
- **diagnostics**：启动报告抬头支持自定义标识（版本号、构建号、实例、机房），渲染成抬头下的一行「标识」，
  同时出现在 `/actuator/chaos` 响应里。静态值配 `chaos.diagnostics.startup-report.identifiers`，
  运行期才知道的值（hostname、Pod 名、可用区）注册 `ChaosStartupIdentifierContributor` Bean；
  同名 key 以配置为准，这样线上改标识不必改代码重新发布。贡献者抛异常只记 debug 日志、不影响启动，
  值按与其他配置相同的规则脱敏。注意中文 key 必须写成 `"[中文]"`，否则 Spring 宽松绑定会剥掉这些字符。
- **autoconfigure**：内置带框架版本号的 banner（`classpath:com/michael/chaos/banner.txt`），
  版本号由 Maven 资源过滤在框架构建期烧入，显示的一定是实际引入的框架版本；三个 archetype 生成的项目默认启用，
  删掉 `spring.banner.location` 即回到 Spring Boot 默认 banner。

### Fixed（1.0 易用性：启动诊断）

- **authorization**：`chaos.authorization.production-safety.profiles` 默认值由 `prod,production` 改为 `prod,production,prd`，
  与全局 `chaos.production-safety.profiles` 一致（默认值统一定义在 `ProductionProfiles.DEFAULTS`）；此前以 `prd` 启动的授权服务器会跳过生产安全检查。

### Added（1.0 模块结构调整）

- **test-support**：新增发布模块 `chaos-test-support`（test scope 引入）：`@WithChaosContext` / `ChaosTestContext` 上下文夹具、
  `TestLoginUsers` / `ChaosSecurityTestSupport` / `ChaosMockMvcSecurity` 登录用户辅助、`InMemoryRedisTemplates`、
  `ChaosContainers` / `ChaosTestProperties` Testcontainers 辅助、`ProductionSafetyTestSupport`。
- **architecture**：新增 `DependencyDirectionArchitectureTest`，把模块依赖方向规则 1~9 固化为测试（core 框架无关、security-api 不依赖 Spring、
  gateway/mybatis/redis 只依赖 security-api、redis 无安全概念、autoconfigure 只含装配代码、starter 与 test-support 的依赖约束、包与模块一一对应），
  规则说明与依赖图见 `docs/architecture.md` 1.1 节。架构测试包由 `com.michael.chaos.test.architecture` 改为 `com.michael.chaos.architecture`。
- **docs**：新增 `modules/chaos-test-support.md`、`modules/chaos-security-api.md`、`modules/chaos-security-redis.md`。

### Changed（1.0 模块结构调整）

- **autoconfigure**：16 个 `chaos-*-autoconfigure` 模块与 `chaos-autoconfigure-support` 合并为唯一的 `chaos-autoconfigure`，
  自动装配类移入 `com.michael.chaos.autoconfigure.<feature>` 子包（例如 `com.michael.chaos.autoconfigure.web.ChaosWebAutoConfiguration`）。
  功能库与三方框架在该模块中全部为 optional，每个自动装配以 `@ConditionalOnClass` 声明依赖；新增 `OptionalDependencyIsolationTest`
  物理移除依赖验证不会出现 `NoClassDefFoundError`。starter 聚合 `chaos-autoconfigure` + 功能库。
- **autoconfigure**：授权服务器自动装配的 spring-data-redis、spring-jdbc 为可选依赖（使用 `store-type=jdbc` 时自行引入
  `spring-boot-starter-jdbc`）；网关自动装配仅在响应式应用中生效，授权服务器自动装配仅在 Servlet 应用中生效。
- **security/mybatis**：`chaos-security` 对 `chaos-audit`、`chaos-mybatis` 对 `chaos-security-api` 改为必需依赖（此前为 optional，缺失时会在类加载阶段失败）。

### Security

- **gateway**：新增 `GatewayRequestSanitizeFilter`，无条件剥离客户端传入的内部身份头（`chaos.gateway.internal-headers`），
  认证成功后按 token claim 重新写入；请求头租户与 token 租户不一致时返回 403。
- **web/core**：`TraceFilter` 默认不再信任 `X-User-Id` / `X-Tenant-Id`（`chaos.web.forwarding.trust-identity-headers` 显式开启）；
  `RequestContextSnapshot` 校验租户/用户 ID 格式，非法值置空。
- **mybatis**：修复租户 SQL 改写与数据权限值未转义导致的 SQL 注入（`SqlLiterals`、`ChaosTenantLineHandler`）。
- **gateway/web**：客户端 IP 仅在直连地址属于 `trusted-proxies` 时才解析 `X-Forwarded-For`，修复黑名单与按 IP 限流可被伪造绕过。
- **gateway**：白名单匹配拒绝 `..`、`%2f`、`;` 等歧义路径，修复路径穿越绕过鉴权/租户/限流；限流改用独立 `rate-limit.skip-paths`。
- **gateway**：JWT 校验 `iss`、`aud`、签名算法白名单。
- **authorization**：注销时撤销 access token；新增登录失败锁定（`chaos.authorization.login-lock.*`）；支持 JWK 轮换（`jwk.previous-public-keys`）。
- **autoconfigure-support/authorization**：生产安全检查默认 fail-fast，生产 profile 下危险默认实现阻断启动。

### Fixed

- **mq**：Kafka 发送结果被丢弃导致 outbox 误标 SENT；RocketMQ 改同步发送并校验结果。
- **mq**：outbox 派发链路未自动装配；claim 超时回收后状态被旧实例覆盖；`last_error` 超长导致消息卡在 SENDING。
- **redis/web**：`RedissonIdempotentRepository` 未实现 `remove()`；`IdempotentInterceptor` 在异常被全局处理后不释放 key。
- **web**：`JacksonCustomizer` 覆盖 Boot 注册的全部 Jackson 模块；`ResultResponseBodyAdvice` 包装 `/error`、actuator 与 String 响应；
  `GlobalExceptionHandler` 把 404/403/405 映射为 500 或错误码不一致。
- **web/gateway**：内存限流器 key 表写满后拒绝所有新请求，改为 LRU 淘汰。
- **service**：TaskDecorator 按名称条件装配导致用户自定义后两者都失效；默认重试策略重试 `BizException`。
- **tenant/security**：`TenantContext`、`RequestContext` 在线程复用时未清理；`DataScopeContext.set` 破坏嵌套作用域。
- **audit**：JDBC 审计随业务事务回滚丢失、超长字段写入失败；日志审计未脱敏。
- **mybatis**：分页无上限；不引入 chaos-security 时审计与逻辑删除字段不填充。
- **security/gateway**：opaque token introspection 无超时、无缓存。
- **job**：多实例下定时任务重复执行（新增 `DistributedJobRunner`）。
- **file**：objectKey 未校验路径穿越、预签名 TTL 为空或超限时异常。
- **examples**：示例 jar 未 repackage 无法 `java -jar` 启动；`example-order-service` 默认要求 Nacos 配置导致无法启动。

### 值得注意的默认行为

这些默认值与常见预期不同，接入时容易踩到：

- 可靠消息的写入端注入点是 `OutboxPublisher`，`ReliableMessagePublisher` 不是 `MessagePublisher`。
- `@Idempotent` 默认要求请求携带幂等 key，缺失返回 400（`requireKey=false` 可关闭）。
- 405 / 415 使用与状态码一致的独立错误码；XSS 输入过滤默认关闭（推荐输出端编码）。
- MyBatis 分页默认上限 500 条；对象存储预签名 TTL 超过 7 天抛 `IllegalArgumentException`。

### Changed

- **build**：`chaos-dependencies` 改为无 parent 的独立 BOM，并成为全部依赖版本的唯一来源；根 POM 以其为 parent，不再重复维护
  `dependencyManagement`。所有 POM 版本改为 `${revision}`（默认值在 `.mvn/maven.config`），由 `flatten-maven-plugin` 在发布时替换。
- **build**：Spring Boot `3.5.0` → `3.5.16`，Spring Cloud `2025.0.0` → `2025.0.3`，Testcontainers `1.21.0` → `1.21.4`
  （包含 Spring Framework / Spring Security / Spring Cloud Gateway 多个已公开 CVE 的修复）。
- **starters**：`chaos-autoconfigure` 中的功能库与三方框架一律 `optional`，由各 starter 按需带入；
  每个 starter 显式声明 `chaos-autoconfigure` 加上自己需要的框架。
- **build**：`chaos-architecture-tests`、`chaos-examples`、`chaos-docs` 不发布，也不进入 BOM。

### Fixed

- **build**：BOM 曾声明不存在的 `chaos-iam-*`、`chaos-nacos`，又漏掉部分真实模块；架构测试现在双向校验 BOM。
- **build**：承载 `@ConfigurationProperties` 的库模块引入 configuration processor，`chaos.*` 配置在 IDE 中有元数据提示。
- **ci**：`scripts/verify-structure.sh` 在缺少 ripgrep 时会静默通过，现在回退到 grep 并正确区分“无匹配”和“命令失败”。
- **ci**：`chaos-integration-test` profile 纳入 CI；架构测试中两条依赖注解格式的断言改为结构化匹配。

### Added

- **ci**：CI 拆分为 structure / build / integration-test / python / smoke 五个 job，增加并发取消、失败报告上传；
  新增 CodeQL 与 Dependabot；新增 `chaos-coverage`（JaCoCo）profile。
- **release**：`chaos-release` profile 绑定 japicmp API 兼容检查（基线缺失时跳过）、Maven Central Portal 发布插件和发布元数据校验。
- **release**：防止 API 兼容门禁空转。japicmp 被跳过时不输出任何内容，发布日志与真跑过一遍无法区分，
  而发布到 Central 不可撤销。`chaos-release` profile 在 `validate` 阶段用 enforcer 拦住两种情况：
  `revision != 1.0.0` 却仍 `chaos.api.check.skip=true`、`chaos.release.compareVersion` 与 `revision` 相同
  （拿自己和自己比永远零差异通过）。基线制品缺失导致的静默跳过 enforcer 管不到，由新增的
  `scripts/verify-api-compatibility.sh` 在发布验证后统计实际产出的兼容性报告数量来兜底。
