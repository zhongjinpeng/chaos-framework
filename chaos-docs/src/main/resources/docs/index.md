# chaos-framework Documentation

## 我要……

新同学先看 [5 分钟上手](getting-started.md)。按要搭建的服务选一行：

| 我要… | 引入（或用 archetype 生成） | 最小配置 | 生产检查清单 | 诊断 |
| --- | --- | --- | --- | --- |
| 写一个 Servlet 业务服务 | `chaos-web-service-starter`（`chaos-archetype-web-service`） | [开发](templates/web-service/application.yml) / [生产](templates/web-service/application-prod.yml) | [业务服务](checklists/web-service.md) | [启动报告](diagnostics.md#1-启动报告) |
| 搭 API 网关 | `chaos-gateway-starter`（`chaos-archetype-gateway`） | [开发](templates/gateway/application.yml) / [生产](templates/gateway/application-prod.yml) | [网关](checklists/gateway.md) | [错误提示目录](diagnostics.md#4-启动失败与配置错误提示目录) |
| 搭 OAuth2 授权服务器 | `chaos-auth-server-starter`（`chaos-archetype-auth-server`） | [开发](templates/auth-server/application.yml) / [生产](templates/auth-server/application-prod.yml) | [授权服务器](checklists/auth-server.md) | [启动报告](diagnostics.md#1-启动报告) |
| 写非 Web 服务（MQ 消费者、批处理） | `chaos-application-starter` | 按需追加能力模板 | 参考[业务服务](checklists/web-service.md)中的数据、Redis 部分 | [启动报告](diagnostics.md#1-启动报告) |
| 接数据库 / 多租户 | `+ chaos-mybatis-starter` | [addons/mybatis.yml](templates/addons/mybatis.yml) | [业务服务 §5](checklists/web-service.md#5-数据与多租户使用-chaos-mybatis-时) | 缺少租户上下文、租户 ID 非法的报错说明 |
| 上线多实例 | `+ chaos-redis-starter` | [addons/redis.yml](templates/addons/redis.yml) | 各场景清单 §1 | 生产安全检查报错会列出缺少的实现 |
| 发可靠消息 | `+ chaos-mq-starter` + 建 outbox 表 | [addons/mq.yml](templates/addons/mq.yml) | [chaos-mq-jdbc](modules/chaos-mq-jdbc.md) | outbox 表缺失时启动 WARN 给出建表脚本 |
| 存文件 | `+ chaos-storage-starter` | [addons/storage.yml](templates/addons/storage.yml) | [chaos-storage](modules/chaos-storage.md) | — |

## Start Here

**使用 chaos 搭建服务**

- [5 分钟上手](getting-started.md)
- [项目脚手架：archetype](modules/chaos-archetypes.md)
- [Starter 选型：该依赖哪些模块](modules/chaos-starters.md)
- [chaos-boot-parent：推荐的应用 parent](modules/chaos-boot-parent.md)
- [配置模板：开发 / 生产最小配置](templates/README.md)
- [生产检查清单：业务服务](checklists/web-service.md) · [网关](checklists/gateway.md) · [授权服务器](checklists/auth-server.md)
- [Configuration Reference：全部配置项（自动生成）](configuration-reference.md)
- [启动诊断：启动报告、/actuator/chaos 与错误提示](diagnostics.md)

**框架设计与治理**

- [Architecture](architecture.md)（含依赖方向规则）
- [CI And Smoke](ci-and-smoke.md)
- [Configuration Index](configuration-index.md)
- [Integration Test](integration-test.md)
- [Module Granularity](module-granularity.md)
- [Optimization Roadmap](optimization-roadmap.md)
- [Release Governance](release-governance.md)
- [Module Manuals](#module-manuals)
- [Cross-Module Capabilities](#cross-module-capabilities)

## Module Manuals

每个手册对应仓库中真实存在的 Maven 模块（或一组 starter），按目录结构分组。

**基础层（chaos-foundation）**

- [chaos-core](modules/chaos-core.md)
- [chaos-domain](modules/chaos-domain.md)

**可观测与审计**

- [chaos-trace](modules/chaos-trace.md)（含指标约定）
- [chaos-audit](modules/chaos-audit.md)
- [chaos-audit-jdbc](modules/chaos-audit-jdbc.md)

**Web 与应用层**

- [chaos-web](modules/chaos-web.md)
- [chaos-service](modules/chaos-service.md)
- [chaos-tenant](modules/chaos-tenant.md)
- [chaos-job](modules/chaos-job.md)

**安全（chaos-security）**

- [chaos-security](modules/chaos-security.md)
- [chaos-security-api](modules/chaos-security-api.md)（与 Spring Security 无关的安全契约）
- [chaos-security-redis](modules/chaos-security-redis.md)（JWT 黑名单统一实现与授权服务器 Redis 存储）
- [chaos-authorization](modules/chaos-authorization.md)

**网关与云**

- [chaos-gateway](modules/chaos-gateway.md)
- [chaos-gateway-nacos](modules/chaos-gateway-nacos.md)
- [chaos-cloud](modules/chaos-cloud.md)
- [chaos-cloud-nacos](modules/chaos-cloud-nacos.md)

**数据访问（chaos-data）**

- [chaos-mybatis](modules/chaos-mybatis.md)
- [chaos-redis](modules/chaos-redis.md)

**消息（chaos-mq）**

- [chaos-mq](modules/chaos-mq.md)
- [chaos-mq-jdbc](modules/chaos-mq-jdbc.md)
- [chaos-mq-kafka](modules/chaos-mq-kafka.md)
- [chaos-mq-rocketmq](modules/chaos-mq-rocketmq.md)

**对象存储（chaos-storage）**

- [chaos-storage](modules/chaos-storage.md)
- [chaos-storage-minio](modules/chaos-storage-minio.md)
- [chaos-storage-oss](modules/chaos-storage-oss.md)

**自动装配与 starter**

- [chaos-autoconfigure](modules/chaos-autoconfigure.md)
- [chaos-starters](modules/chaos-starters.md)（场景 starter 与能力 starter 选型）
- [chaos-boot-parent](modules/chaos-boot-parent.md)
- [chaos-archetypes](modules/chaos-archetypes.md)（web-service / gateway / auth-server 项目脚手架）

> 单个 starter 没有独立文档页：starter 只聚合依赖，选型看 [chaos-starters](modules/chaos-starters.md)，
> 配置与用法看对应的库模块文档（例如 `chaos-mq-starter` → [chaos-mq](modules/chaos-mq.md)）。

**构建、治理与示例**

- [chaos-dependencies](modules/chaos-dependencies.md)
- [chaos-test-support](modules/chaos-test-support.md)（业务项目测试支持，test scope）
- [chaos-architecture-tests](modules/chaos-architecture-tests.md)（含依赖方向规则）
- [chaos-docs](modules/chaos-docs.md)
- [chaos-examples](modules/chaos-examples.md)

## Cross-Module Capabilities

以下能力没有独立 Maven 模块，契约与实现分布在多个模块中：

- [分布式锁（chaos-core + chaos-redis）](capabilities/distributed-lock.md)
- [幂等（chaos-core + chaos-web + chaos-mq + chaos-redis）](capabilities/idempotency.md)
- [缓存 key 策略（chaos-domain + chaos-redis）](capabilities/cache-key.md)
- [日志约定与模板（chaos-trace）](capabilities/logging.md)
- [错误文案国际化（chaos-core + chaos-web）](capabilities/i18n.md)
- [治理指标与健康检查（chaos-core + chaos-trace）](capabilities/metrics.md)

## Proposals

尚未在本仓库落地的设计方案，仅供参考：

- [chaos-iam](proposals/chaos-iam.md)

## Recommended Reading Order

1. `architecture.md`
2. `chaos-dependencies`
3. one starter manual: `chaos-web`, `chaos-service`, `chaos-gateway`, or `chaos-authorization`
4. the matching infrastructure modules, such as `chaos-redis` or `chaos-mybatis`
5. `configuration-index.md` and `chaos-autoconfigure` (production safety) before going live
