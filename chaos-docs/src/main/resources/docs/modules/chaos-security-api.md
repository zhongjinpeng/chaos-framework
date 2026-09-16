# chaos-security-api

## 职责

与 Spring Security **无关**的安全契约，只依赖 `chaos-core`：

| 包 | 内容 |
| --- | --- |
| `com.michael.chaos.security.api.auth` | `LoginUser`、`LoginUserProvider`（获取当前登录用户的 SPI）、`ChaosJwtClaims`（claim 名称常量） |
| `com.michael.chaos.security.api.token` | `JwtRevocationService`、`NoopJwtRevocationService`、`JwtTokenIds`、`TokenIntrospectionCache` |
| `com.michael.chaos.security.api.access` | RBAC / ABAC 授权模型：`AuthorizationManager`、`AuthorizationRequest`、`RbacAuthorizationPolicy`、`AbacAuthorizationPolicy` 等 |
| `com.michael.chaos.security.api.datascope` | `DataScopeContext`、`DataScopeRequest`、`DataScopeAuthorizationService` |

## 为什么单独拆出

网关（WebFlux）、持久层（chaos-mybatis）和 Redis 适配只需要 claim 名称、撤销 SPI、数据权限上下文这些契约。
它们不依赖 `chaos-security`，因此 Servlet 过滤器、AOP、aspectjweaver 不会被带进网关 classpath。
2.0 起这些模块只依赖 `chaos-security-api`，并由架构测试固化：

- 规则 2：`chaos-security-api` 只能依赖 `chaos-core`，源码不得 import `org.springframework.*`；
- 规则 3：`chaos-gateway`、`chaos-gateway-nacos`、`chaos-mybatis`、`chaos-redis` 不得依赖 `chaos-security` / `chaos-authorization` / `chaos-security-redis`。

## 使用

- 业务服务通常不直接依赖本模块：`chaos-security-starter`、`chaos-gateway-starter` 已包含。
- 编写只需要安全契约的扩展（自定义数据权限、网关插件、消息消费端鉴权）时依赖本模块，而不是 `chaos-security`。
- `JwtRevocationService` 以 token ID 为参数：`JwtTokenIds.resolve(jti, tokenValue)` 计算 ID，`JwtTokenIds.revocationTtl(expiresAt, now)` 计算黑名单 TTL，
  授权服务器、资源服务器和网关必须使用同一算法。

更多安全模块说明见 [chaos-security](chaos-security.md)。
