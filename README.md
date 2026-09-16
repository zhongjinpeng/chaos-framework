# Chaos Framework

基于 Java 21、Spring Boot 3.5、Spring Cloud 2025.0 的企业级服务脚手架，按 DDD 与六边形架构提供开箱即用的基础能力：
统一响应与异常、JWT / opaque 资源服务器与 OAuth2 授权服务器、API 网关治理、多租户、审计、可靠消息（outbox）、
分布式锁与限流、对象存储、W3C Trace，以及启动诊断与生产安全检查。

## 我要……

| 我要搭建 | 引入 | 或者直接生成项目 |
| --- | --- | --- |
| Servlet 业务服务 | `chaos-web-service-starter` | `chaos-archetype-web-service` |
| API 网关 | `chaos-gateway-starter` | `chaos-archetype-gateway` |
| OAuth2 授权服务器 | `chaos-auth-server-starter` | `chaos-archetype-auth-server` |
| 非 Web 服务（MQ 消费者、批处理） | `chaos-application-starter` | — |

按需追加能力 starter：`chaos-mybatis-starter`（数据库、多租户）、`chaos-redis-starter`（生产必需：集群限流、幂等、token 黑名单）、
`chaos-mq-starter`、`chaos-job-starter`、`chaos-storage-starter`、`chaos-cloud-starter`、`chaos-cloud-nacos-starter`。

## 快速开始

```bash
mvn archetype:generate -B \
  -DarchetypeGroupId=com.michael \
  -DarchetypeArtifactId=chaos-archetype-web-service \
  -DarchetypeVersion=1.0.0 \
  -DgroupId=com.acme -DartifactId=todo-service -Dpackage=com.acme.todo
cd todo-service
mvn verify            # 离线即可通过
mvn spring-boot:run   # 日志中输出 Chaos 启动报告
```

手工接入：以 `com.michael:chaos-boot-parent:1.0.0` 为 parent，引入场景 starter，从配置模板复制 `application.yml`。
完整步骤见 [5 分钟上手](chaos-docs/src/main/resources/docs/getting-started.md)。

## 文档

| 主题 | 链接 |
| --- | --- |
| 文档首页（场景决策表） | [docs/index.md](chaos-docs/src/main/resources/docs/index.md) |
| 该依赖哪些 starter | [Starter 选型](chaos-docs/src/main/resources/docs/modules/chaos-starters.md) |
| 该配什么 | [配置模板](chaos-docs/src/main/resources/docs/templates/README.md) · [配置参考（自动生成）](chaos-docs/src/main/resources/docs/configuration-reference.md) |
| 上线前检查 | [业务服务](chaos-docs/src/main/resources/docs/checklists/web-service.md) · [网关](chaos-docs/src/main/resources/docs/checklists/gateway.md) · [授权服务器](chaos-docs/src/main/resources/docs/checklists/auth-server.md) |
| 启动报告与错误提示 | [启动诊断](chaos-docs/src/main/resources/docs/diagnostics.md) |
| 架构与依赖方向规则 | [Architecture](chaos-docs/src/main/resources/docs/architecture.md) |
| 版本变更记录 | [CHANGELOG](CHANGELOG.md) |

## 构建框架本身

```bash
./mvnw -B test                                              # 全量单元测试（含架构测试）
./mvnw -B -Pchaos-integration-test verify                   # 集成测试（需要 Docker）
scripts/verify-structure.sh                                 # 结构治理检查
python3 scripts/generate-configuration-reference.py --check # 配置参考文档是否最新（需先编译）
scripts/verify-archetypes.sh                                # 生成 archetype 并验证（会 install 到 ~/.m2）
scripts/smoke-examples-jwt.sh                               # 示例三服务 JWT 链路（需要本机 Redis）
```

仓库结构、模块职责与贡献约定见 [architecture.md](chaos-docs/src/main/resources/docs/architecture.md) 与 [AGENTS.md](AGENTS.md)。
