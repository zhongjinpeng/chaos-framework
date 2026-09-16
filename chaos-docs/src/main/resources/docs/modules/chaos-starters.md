# chaos-starters

## 职责

面向使用方的依赖入口。starter 只聚合依赖、不含 Java 代码；自动装配逻辑全部在 [chaos-autoconfigure](chaos-autoconfigure.md)。

starter 分两层：

- **场景 starter**：按“要搭什么服务”选择，一个应用只选一个。
- **能力 starter**：按“还需要什么能力”追加。

## 怎么选

### 第一步：选场景 starter

| 我要搭建 | 场景 starter | 已包含 |
| --- | --- | --- |
| Servlet 业务服务 | `chaos-web-service-starter` | web（统一响应、全局异常、Trace、日志模板、OpenAPI、Actuator）、security（JWT / opaque 资源服务器、RBAC/ABAC、`@Permission`、`@DataScope`）、tenant、audit、application（事务、重试、领域事件、上下文传播、Prometheus） |
| API 网关 | `chaos-gateway-starter` | Spring Cloud Gateway、鉴权（只依赖 security-api）、租户校验、限流、黑名单、灰度、访问日志、审计、Trace、Actuator、Prometheus |
| OAuth2 授权服务器 | `chaos-auth-server-starter` | authorization（password / sms grant、登录锁定、JWK 轮换、Redis 存储与 JWT 黑名单）、审计、Actuator、Prometheus |
| 非 Web 服务（MQ 消费者、批处理） | `chaos-application-starter` | 事务、重试、领域事件、上下文传播、Prometheus；按需再加 mq / job / mybatis |

注意：网关是 WebFlux 应用，不要和 `chaos-web-starter` / `chaos-web-service-starter`（Servlet）同时引入。

### 第二步：追加能力 starter

| 还需要 | 能力 starter | 说明 |
| --- | --- | --- |
| 数据库 | `chaos-mybatis-starter` | MyBatis-Plus、分页上限、多租户 SQL 改写、数据权限、审计字段、乐观锁 |
| Redis | `chaos-redis-starter` | Redisson、分布式锁、集群限流、幂等存储、布隆过滤器、延迟队列；**多实例 / 生产环境必需** |
| 可靠消息 | `chaos-mq-starter` | outbox + Kafka / RocketMQ |
| 定时任务 | `chaos-job-starter` | `@EnableScheduling` + 分布式锁互斥执行 |
| 对象存储 | `chaos-storage-starter` | MinIO / OSS |
| 服务间调用 | `chaos-cloud-starter` | OpenFeign + LoadBalancer + trace 透传（Servlet）；响应式服务用 `chaos-cloud-reactive-starter` |
| Nacos | `chaos-cloud-nacos-starter` / `chaos-gateway-nacos-starter` | 注册与配置约定 / 网关动态路由 |
| 审计落库 | `chaos-audit-jdbc-starter` | JDBC 审计持久化 |

以下 starter 已被场景 starter 包含，一般不需要单独引入：`chaos-web-starter`、`chaos-security-starter`、`chaos-tenant-starter`、
`chaos-audit-starter`、`chaos-authorization-starter`。只想要其中单项能力（例如只要统一响应、不要安全）时才单独使用。

## 示例

推荐以 [chaos-boot-parent](chaos-boot-parent.md) 为 parent：

```xml
<parent>
    <groupId>com.michael</groupId>
    <artifactId>chaos-boot-parent</artifactId>
    <version>1.0.0</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>com.michael</groupId>
        <artifactId>chaos-web-service-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.michael</groupId>
        <artifactId>chaos-mybatis-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.michael</groupId>
        <artifactId>chaos-redis-starter</artifactId>
    </dependency>
</dependencies>
```

配置从 [配置模板](../templates/README.md) 开始：场景模板 + 能力模板，开发配置不超过 10 个 `chaos.*` 配置项。

## 约束（架构测试校验）

- starter 不含 Java 代码，不依赖示例和测试模块。
- 场景 starter 只聚合能力 starter，外加 Actuator / Prometheus；不直接依赖 chaos 库模块或三方框架（依赖方向规则 10）。
- 能力 starter 不得依赖场景 starter。
- 能力 starter 显式声明 `chaos-autoconfigure` + 功能库 + 所需三方框架（`chaos-autoconfigure` 中它们都是 optional）。
- 新增 starter 必须登记到 `chaos-dependencies`。
