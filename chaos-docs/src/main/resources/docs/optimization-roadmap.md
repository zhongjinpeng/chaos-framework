# chaos-framework 优化路线

## 目标

持续把 `chaos-framework` 从通用脚手架演进为企业级微服务治理平台。所有优化必须保持模块边界清晰、starter 只做依赖聚合、autoconfigure 独立、业务代码不侵入 framework。

> **这份文档只记录"要做什么"和"做到什么程度算完成"。**
> 每一项具体改了哪些类、哪些配置，看仓库根目录的 `CHANGELOG.md`；
> 能力的用法与边界，看对应的[能力文档](index.md#cross-module-capabilities)或模块文档。
> 此前这里还为每个已完成项复制了一份详细叙述，与 CHANGELOG 完全重叠，两边措辞已经开始漂移，故移除。

## 执行原则

- 优先补齐已经声明但尚未形成闭环的能力。
- 优先做可配置、可替换、可测试的基础能力。
- 每完成一项同步更新模块文档、架构测试和示例。
- 不引入巨型 common，不新增无边界 util。
- 不使用 `spring.factories`，只使用 Spring Boot 3 `AutoConfiguration.imports`。

## 未决事项

以下是已知但尚未排期的项，按影响面排序。

### 工程门禁

| 事项 | 现状 | 备注 |
| --- | --- | --- |
| CI 首次运行 | 仓库已推送到 GitHub，但 Actions 尚未在其上跑过一次 | CI、CodeQL、Dependabot、dependency-review 的实际行为有待首次运行验证 |
| 覆盖率门槛 | `chaos.coverage.minimum` 为 `0.00` | 建议改为按模块设差异化门槛，优先把 chaos-security-redis、chaos-authorization 卡到 70% 指令 / 55% 分支 |
| javadoc doclint | 仍为 `none` | 需要先清理注释中的非法 HTML |
| API 兼容检查暂时关闭 | `chaos.api.check.skip=true` | 1.0.0 是首发版本没有可比基线。发布 1.0.1 时必须改回 `false` 并把 `compareVersion` 设为上一个已发布版本 —— 忘了改的话 `chaos-release` profile 的 enforcer 会在 `validate` 阶段直接拦住（见 [release-governance.md](release-governance.md) 防止门禁空转），不再依赖人记得 |
| 架构测试断言强度 | `LayerBoundaryArchitectureTest` 仍有约 130 处 `Files.readString(...).contains(...)` | 最空洞的三个网关断言已替换为真实请求测试；彻底方案是引入 ArchUnit 做字节码级依赖检查 |

### 能力缺口

| 事项 | 现状 | 备注 |
| --- | --- | --- |
| 缓存 | 全仓无 `CacheManager` / `@EnableCaching`，但 `chaos-application-starter` 已引入 `spring-boot-starter-cache` | `CacheKeyStrategy` 未接到 Spring Cache 上，`chaos.redis.key-prefix` 对 `@Cacheable` 不生效。要么补 `chaos-cache`（Caffeine + Redis 两级、防穿透/击穿），要么从 starter 摘掉该依赖 |
| 熔断隔离 | 无 resilience4j / spring-cloud-circuitbreaker | 网关只有统一 JSON 降级响应，没有真熔断、舱壁、超时预算；Feign 侧同样没有 |
| 定时任务 | `chaos-job` 只有分布式锁互斥 + `@Scheduled` | 无分片、失败重试、执行记录、手动触发。要么补 XXL-Job / PowerJob 适配，要么在文档中明确"只做互斥" |
| 响应式业务栈 | `chaos-web` 仅 Servlet | WebFlux 业务服务拿不到统一 `Result`、全局异常、限流、幂等、XSS；`chaos-cloud-reactive-starter` 目前只给了 loadbalancer + actuator + validation |
| 虚拟线程 | 全仓未提及 `spring.threads.virtual` | Java 21 + Boot 3.5 下的常见生产开关，影响 ThreadLocal 上下文传播、`TaskDecorator` 生效路径与 Redisson 池化，既无文档也无测试 |
| 限流失败告警 | 网关 `chaos.gateway.rate-limit.fail-open` 默认 `true` | 限流存储故障时会静默放行。已有 `chaos.ratelimit.errors{outcome="fail-open"}` 指标，还需要在部署侧配置对应告警规则 |
| 集成测试覆盖面 | 4 个 `*IT` 类、487 行 | RocketMQ、MinIO / OSS、Nacos、网关端到端、授权服务器 JDBC 存储均无集成测试；`smoke` job 也只在主干 push 时运行 |

## 已交付能力与验收标准

下表是历史交付记录，保留是因为「验收标准」一列说明了每项能力做到什么程度才算闭环，
新增同类能力时可作为参照。全部已完成。

| 优先级 | 状态 | 优化项 | 目标 | 验收标准 |
| --- | --- | --- | --- | --- |
| P0 | 已完成 | Gateway 全局限流闭环 | 补齐网关入口限流治理 | 支持按 IP、用户、租户、路径、路由组合限流；支持本地默认实现；支持 fail-open/fail-closed；429 统一 JSON 响应；有单测和文档 |
| P0 | 已完成 | Gateway 熔断降级标准化 | 统一后端不可用时的错误响应 | 下游连接失败、超时和未知异常输出统一 JSON；保留 traceId；后续可扩展到 Resilience4j |
| P0 | 已完成 | Refresh Token 安全增强 | 防止 refresh token 重放和异常复用 | 支持 refresh 成功审计、重放嫌疑审计；Redis 授权存储清理旧 refresh token 索引；补充测试 |
| P0 | 已完成 | 审计持久化扩展示例 | 让审计从日志兜底扩展到落库/MQ | 提供 JDBC 落库实现；包含表结构；文档说明敏感字段脱敏 |
| P1 | 已完成 | 租户生命周期模块 | 支撑 SaaS 租户状态治理 | 新增 `chaos-tenant`；支持租户启停、冻结、套餐和隔离模式；Gateway/Security/MyBatis 可读取租户状态 |
| P1 | 已完成 | 数据权限增强 | 提升复杂业务数据权限表达能力 | 支持表别名、多字段、IN/LIKE/BETWEEN、角色/部门/用户组合条件；空条件继续默认拒绝 |
| P1 | 已完成 | MQ 可靠消息 | 提供企业级消息一致性基础 | 支持 outbox、发送确认、消费幂等、死信处理、重试和告警审计 |
| P1 | 已完成 | 配置治理增强 | 降低 starter 使用成本 | 所有配置类补齐校验；输出配置元数据；文档列出配置表；危险默认值启动提示 |
| P2 | 已完成 | OpenTelemetry 兼容层 | 提升可观测生态兼容性 | 支持 W3C Trace Context 和 baggage；Web、Gateway、Feign、MQ 一致透传；保持 SkyWalking agent 接入 |
| P2 | 已完成 | Testcontainers 集成测试 | 提升真实基础设施验证能力 | Redis、MySQL、Kafka 关键链路有集成测试；RocketMQ 使用独立 profile 策略 |
| P2 | 已完成 | 示例工程完整链路 | 给引用方可运行落地样板 | 增加 auth-server、gateway、order-service；覆盖登录、鉴权、租户、MyBatis、Redis/reference token、互踢、Prometheus |
| P2 | 已完成 | 发布治理 | 支撑 framework 产品化发布 | BOM 兼容矩阵、依赖收敛、API 变更检查、changelog、starter 依赖树文档 |
| P0 | 已完成 | 授权服务器生产安全检查 | 提示生产环境中的开发默认值 | prod/production profile 下检查 localhost issuer、noop secret、memory client、临时 JWK；同时识别内存授权仓储、Noop JWT 撤销、空审计和互踢本地索引；默认 fail-fast 阻断启动（`chaos.authorization.production-safety.fail-fast=false` 可降级为告警） |
| P0 | 已完成 | 异步上下文传播治理 | 避免线程池和异步任务 trace/租户/MDC 丢失或串扰 | 提供 TraceContextSnapshot、Scope、Runnable wrap 和 TaskDecorator 自动装配；补充测试与文档 |
| P0 | 已完成 | CI 与示例链路 Smoke | 提升提交质量和示例可验证性 | GitHub Actions 跑单测、发布治理 validate、结构扫描；提供 JWT 示例链路 smoke 脚本 |
| P0 | 已完成 | Noop/InMemory 生产安全保护 | 提示危险兜底实现进入生产 | 支持 `chaos.production-safety.production-mode`；生产模式检测 NoopJwtRevocationService、NoopTenantStatusProvider、NoopDataScopeProvider、本地限流和本地幂等实现，默认 fail-fast 阻断启动（`chaos.production-safety.fail-fast=false` 可降级为告警；默认 profiles 增加 `prd`） |
| P1 | 已完成 | MQ outbox JDBC 适配器 | 让可靠消息从端口走向可落库实现 | 新增 chaos-mq-jdbc、JDBC outbox 仓储、建表脚本、自动装配、测试和文档 |
| P1 | 已完成 | MQ outbox 多实例 claim | 降低多实例派发重复发送窗口 | OutboxMessageRepository 增加 claimDueMessages；派发器走 claim；JDBC 条件 update 抢占并记录 claimed_at/claim_owner |
| P1 | 已完成 | 幂等响应回放 | 让客户端超时重发能拿到首次结果 | 重复请求返回首次状态码/响应体/Location 并带 `Idempotency-Replayed`；执行中返回 409；失败释放占位；提供 Redis 与内存两种快照存储；默认关闭 |
| P1 | 已完成 | 错误文案国际化 | 消除中英混杂，让 `Accept-Language` 生效 | 错误码与框架内置提示统一走 `MessageSource`；提供英文根包与 zh_CN 资源包；业务可用同名 key 覆盖；可固定语言或整体关闭 |
| P0 | 已完成 | 治理指标与健康检查 | 让框架自己的决策可观测 | 限流/幂等/租户/权限/认证/outbox/审计共 12 个指标；无 Micrometer 时降级为空实现；outbox 积压进 /actuator/health；标签基数受控 |
| P1 | 已完成 | 审计异步化 | 消除请求线程内的同步审计写入 | 有界队列 + 守护线程；队列满丢弃并计指标不阻塞业务；关闭时排空；默认关闭 |
| P1 | 已完成 | 架构测试去假保证 | 让约束真的被验证 | 三个"源码含子串"的网关测试替换为 Result 形状比对与真实请求断言 |
| P1 | 已完成 | 供应链门禁 | 补上依赖 CVE 与物料清单 | CycloneDX SBOM 绑定 release profile；enforcer bannedDependencies；CI 加 dependency-review |
| P1 | 已完成 | chaos-python 收口 | 让 Python 侧不再是孤儿模块 | 补 ASGI/WSGI HTTP 中间件；版本对齐框架并由 CI 校验；CI 增加 ruff 与 mypy strict |
| P0 | 已完成 | 工程基建治理（2026-09） | 让依赖、发布和 CI 门禁真正生效 | BOM 单一来源并双向校验；Boot/Cloud 补丁升级；autoconfigure 依赖 optional；配置元数据覆盖库模块；CI 拆分 job 并纳入集成测试、覆盖率、CodeQL、Dependabot；结构扫描不再静默通过；japicmp 与 Central 发布绑定 release profile |
