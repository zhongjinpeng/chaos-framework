# 模块粒度设计决策

## 当前拆分原则

每个功能域拆分为三层：
1. **核心契约**（如 `chaos-core` 中的 `com.michael.chaos.core.lock`、`com.michael.chaos.core.idempotent`）— 纯 Java 接口，不依赖 Spring
2. **统一自动装配模块** `chaos-autoconfigure`（如 `com.michael.chaos.autoconfigure.redis`）— 全部 Spring Boot 自动装配逻辑，功能库与三方框架均为 optional
3. **starter 模块**（如 `chaos-redis-starter`）— 纯依赖聚合，无 Java 代码

## 为什么锁、幂等、缓存没有独立模块

早期规划过 `chaos-lock`、`chaos-idempotent`、`chaos-cache` 独立模块，实际落地时发现这些契约只有少量接口，
独立成模块会让 BOM、starter 和文档的维护成本远大于收益，因此：

- 契约接口放在 `chaos-core`（锁、幂等）和 `chaos-domain`（缓存 key 策略）；
- Redis 实现统一放在 `chaos-redis`，由 `chaos-redis-starter` 引入；
- Web 幂等拦截器在 `chaos-web`。

同理，OpenFeign 透传拦截器在 `chaos-trace`（由 `chaos-autoconfigure` 的 cloud 包注册），Nacos 约定在 `chaos-autoconfigure` 的 cloud.nacos 包，MDC/日志模板在 `chaos-trace`。

## 设计理由

- 核心契约不依赖 Spring，保证领域层可独立测试和复用
- 自动装配集中为一个模块：消除跨 jar 拆分包与自动装配模块之间的相互依赖，
  装配顺序、条件与可选依赖在一处治理；starter 仍按功能域拆分，决定引入哪些功能库
- 每个 starter 对应一个功能域，业务方按需引入，避免全量依赖
- 目录按能力域分组，目录名与 artifactId 对齐：`chaos-foundation`、`chaos-audit`、`chaos-data`、`chaos-storage` 等聚合目录只做归类，
  不引入额外坐标；只有 2 个类、且只被 service 装配使用的 Observation 过滤器放在 `chaos-trace` 的 `trace.monitor` 包，避免为极小的能力单独维护模块
- 名称表达职责：`chaos-domain` 放 DDD 基础类型（不叫 common，避免变成大杂烩），`chaos-storage` 与 starter、配置前缀一致，
  `chaos-architecture-tests` 是仓库治理测试，而不是给业务方用的测试支持库
- 契约与实现分离：`chaos-security-api` 只放与 Spring Security 无关的安全契约，网关、持久层、Redis 适配只依赖它；
  Redis 版安全实现集中到 `chaos-security-redis`，`chaos-redis` 保持通用
- 面向使用方的测试支持单独成模块（`chaos-test-support`，test scope），与仓库治理测试 `chaos-architecture-tests` 分开
- 以上边界由 `DependencyDirectionArchitectureTest` 固化（规则见 [architecture.md 1.1](architecture.md#11-依赖方向规则)），新增模块需要登记基础包

## 强关联 starter 对

以下 starter 对在实际使用中几乎总是一起引入，未来可考虑合并：

| 组合 | 理由 |
|------|------|
| `chaos-audit-starter` + `chaos-audit-jdbc-starter` | 审计不落库无意义，JDBC 是唯一持久化实现 |
| `chaos-gateway-starter` + `chaos-gateway-nacos-starter` | 当前 Nacos 是唯一动态路由方案 |
| `chaos-security-starter` + `chaos-redis-starter` | 生产环境 JWT 撤销强依赖 Redis |

## 暂不合并的原因

- 1.0.x 阶段优先保持模块边界清晰，降低单次变更影响范围
- 合并需要同步更新 BOM、文档、示例工程和架构测试
- 等待真实业务方反馈后再决定是否合并

## 评估时机

- 首个外部业务方接入后收集反馈
- 如果超过 80% 的接入方同时引入上述组合，则合并
