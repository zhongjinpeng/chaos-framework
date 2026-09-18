# chaos-security

## 职责

安全能力域聚合目录，包含四个模块：

| 目录 | artifactId | 职责 | 依赖约束 |
| --- | --- | --- | --- |
| `chaos-security/chaos-security-api` | [`chaos-security-api`](chaos-security-api.md) | 与 Spring Security 无关的安全契约：`LoginUser`、`LoginUserProvider`、`ChaosJwtClaims`、`JwtRevocationService`/`JwtTokenIds`、`TokenIntrospectionCache`、RBAC/ABAC 授权模型（`security.api.access`）、数据权限上下文（`security.api.datascope`） | 只依赖 chaos-core |
| `chaos-security/chaos-security` | `chaos-security` | Servlet 资源服务器实现：JWT/opaque 转换、`JwtRevocationFilter`、`SecurityContextRequestFilter`、`@Permission`/`@DataScope` 切面、`SecurityContextLoginUserProvider` | 依赖 security-api + Spring Security |
| `chaos-security/chaos-authorization` | `chaos-authorization` | OAuth2 授权服务器扩展 | 依赖 chaos-security |
| `chaos-security/chaos-security-redis` | [`chaos-security-redis`](chaos-security-redis.md) | 安全领域的 Redis 适配：全框架唯一的 JWT 黑名单实现 `RedisJwtRevocationService`，以及授权服务器 Redis 存储（授权、客户端、授权同意、会话索引、踢人、验证码、登录锁定） | 依赖 security-api；chaos-authorization、spring-data-redis 为可选 |

为什么拆出 `chaos-security-api`：网关（WebFlux）、持久层（chaos-mybatis）和 Redis 适配只需要 claim 名称、撤销 SPI、数据权限上下文等契约，
此前却依赖整个 chaos-security，把 Servlet 过滤器、AOP、aspectjweaver 带进了网关 classpath。现在这些模块只依赖 `chaos-security-api`。

### JWT 黑名单只有一份实现

`JwtRevocationService` 以 token 标识（`JwtTokenIds.resolve(jti, tokenValue)`：优先 jti，否则 token 的 SHA-256）为参数，
授权服务器、资源服务器和网关共用 chaos-security-redis 中的 `RedisJwtRevocationService`：

- 基于 `StringRedisTemplate`，key 为明文 `chaos:security:jwt:blacklist:<tokenId>`，与 Redisson、redis-cli 互通；
- 引入 `chaos-redis-starter` 后由 `ChaosSecurityRedisAutoConfiguration` 自动注册，授权服务器仅引入 Spring Data Redis 时由授权自动装配注册同一实现；
- 旧版本授权服务器使用 `RedisTemplate<Object, Object>`（JDK 序列化 key）写黑名单，资源服务器用 Redisson 按明文 key 查询，两侧互相不可见——
  注销后资源服务器仍放行。2.0 统一实现后该问题消失；升级时旧格式的黑名单记录会随 TTL 自然过期。

## 依赖方式

资源服务和业务服务优先引入 `chaos-security-starter`（已包含 chaos-security-redis）；只需要安全契约的模块（网关过滤器、持久层、消息消费等）依赖 `chaos-security-api`。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-security-starter</artifactId>
</dependency>
```

## 配置

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://auth-server:9000/oauth2/jwks
          # 由 Spring Boot 资源服务器自动装配校验签发方与受众，生产环境建议配置
          issuer-uri: https://auth.example.com
          audiences:
            - chaos-client

chaos:
  security:
    enabled: true
    http-basic-enabled: false
    token:
      type: jwt
    jwt:
      revocation-check-enabled: true
      # 撤销服务（Redis）异常时是否放行；默认 false，返回 503
      revocation-fail-open: false
    access:
      admin-roles:
        - admin
    permit-all:
      - /actuator/health
```

Redis/reference token 资源服务使用 opaque introspection：

```yaml
chaos:
  security:
    token:
      type: opaque
    opaque-token:
      introspection-uri: http://auth-server:9000/oauth2/introspect
      client-id: chaos-client
      client-secret: ${CHAOS_INTROSPECTION_CLIENT_SECRET}
      # 以下为默认值
      cache-ttl: 30s        # 成功结果本地缓存；0 表示关闭
      cache-max-size: 10000
      connect-timeout: 1s
      read-timeout: 3s
```

自动装配注册的 `OpaqueTokenIntrospector` 由两层组成：

1. 带连接/读取超时的 `NimbusOpaqueTokenIntrospector`（Spring Security 默认构造器使用无超时的 RestTemplate）。
2. `CachingOpaqueTokenIntrospector`：只缓存成功结果，key 为 token 的 SHA-256，TTL 取 `cache-ttl` 与 token `exp` 的较小值；
   缓存写满时放弃写入但不影响鉴权。

注意：token 撤销后，最多在 `cache-ttl` 内仍可能被本服务接受。对注销/踢人实时性要求高的场景，把 `cache-ttl` 设为 `0`。
自定义 `OpaqueTokenIntrospector` Bean 会整体替换上述实现。

## 示例

```java
@Permission("order:read")
@GetMapping("/orders/{id}")
public OrderVO detail(@PathVariable String id) {
    return orderQueryService.detail(id);
}

@DataScope("dept")
public List<OrderVO> list(OrderQuery query) {
    return orderQueryService.list(query);
}

public void logout(Jwt jwt) {
    jwtRevocationService.revoke(jwt);
}
```

通用 RBAC/ABAC 决策可直接注入 `AuthorizationManager`：

```java
AccessSubject subject = AccessSubject.from(loginUser);
AuthorizationResource resource = AuthorizationResource.of(
        "order",
        orderId,
        Map.of("tenantId", orderTenantId, "status", orderStatus)
);
AuthorizationDecision decision = authorizationManager.decide(
        AuthorizationRequest.of(subject, "order:read", resource)
);
```

ABAC 策略示例：

```java
AuthorizationPolicy sameTenantPolicy = AbacAuthorizationPolicy.allow(
        "same-tenant",
        Set.of(AuthorizationAction.of("order:read")),
        List.of(AttributeCondition.eq(
                AttributeReference.subject("tenantId"),
                AttributeReference.resource("tenantId")
        ))
);
```

常用条件工厂包括 `eq`、`notEq`、`in`、`notIn`、`exists`、`notExists`、`gt`/`gte`/`lt`/`lte`、`between`、`regex`、`contains`。
属性命名空间包括 subject、resource 和 environment；resource 内置支持 `type`、`id`，subject 内置支持 `userId`、`username`、`tenantId`、`roles`、`permissions`。

日常使用不必手写策略对象：动作 + 资源 + 资源属性用 `@RequireAccess` 声明，策略写在 `chaos.security.access.policies` 配置里。

```java
@RequireAccess(
        action = "order:update",
        resourceType = "order",
        resourceId = "#order.id",
        attributes = @AccessAttribute(name = "ownerId", value = "#order.ownerId"))
public void update(OrderDTO order) { ... }
```

```yaml
chaos:
  security:
    access:
      wildcard-permission-enabled: true
      role-hierarchy:
        admin: [manager]
        manager: [user]
      policies:
        - id: order-owner-only
          effect: DENY
          actions: [order:update]
          conditions:
            - left: resource.ownerId
              operator: NOT_EQ
              right: subject.userId
```

完整说明见[访问控制：RBAC + ABAC](../capabilities/access-control.md)。

## 扩展点

- 优先注册 Spring Bean 覆盖默认实现。
- 不在业务代码中依赖 autoconfigure 类。
- 不跨层调用 infra 实现，domain/application 只依赖接口。
- 覆盖 `AuthorizationManager` 可接入策略中心、OPA、Casbin 或企业权限平台。
- 注册自定义 `AuthorizationPolicy` 可叠加 ABAC 规则，例如同租户、同部门、资源 owner、数据状态、请求环境标签。
- 注册 `PermissionResolver` 可在服务端按角色展开权限（令牌只带角色时使用），对 `@Permission`、`@RequireAccess` 和 `SecurityUtils.hasPermission` 同时生效。
- 注册 `SubjectAttributeResolver` 可补充部门、职级等 ABAC 主体属性；注册 `AuthorizationContextContributor` 可补充自定义环境属性。
- 实现 `AuthorizationPolicySource` 可把策略放到数据库或配置中心，用 `AccessPolicyFactory` 把记录转换成策略。
- 覆盖 `PermissionCheckService` 可兼容历史系统；默认实现已委托 `PermissionAuthorizationService`。
- 覆盖 `JwtRevocationService`（chaos-security-api）可接入数据库或认证中心；Redis 场景直接使用 chaos-security-redis 的统一实现。
- JWT claim 默认读取 `userId`、`username`、`tenantId`、`roles`、`permissions`；`userId` 缺失时用标准 `sub` 兜底，`roles` 与 `permissions` 支持集合或逗号分隔字符串。
- opaque token introspection 默认读取同一组 claim，并转换为统一 `LoginUser`。
- `SecurityContextRequestFilter` 会把登录用户和租户写入 `RequestContext`，供日志、审计和 MyBatis 使用；请求结束后恢复进入前的上下文，避免线程复用时串号。
- `JwtRevocationFilter` 默认开启，发现 JWT 黑名单命中时返回 401 和统一 JSON 错误体（带 `WWW-Authenticate: Bearer error="invalid_token"`）；撤销服务异常时按 `revocation-fail-open` 决定放行或返回 503。
- `SecurityUtils.hasPermission` 在 Spring 环境中委托 `PermissionAuthorizationService`，与 `@Permission` 使用同一套 RBAC/ABAC 决策（含 admin 角色与 DENY 策略）；非 Spring 环境退化为只检查用户权限列表。
- `DataScopeContext.set` 只替换栈顶，不会清空外层嵌套作用域；嵌套场景优先使用 `push`/`pop`。

## 注意事项

- `chaos-security-starter` 面向资源服务器；授权服务器继续使用 `chaos-authorization-starter`。
- 资源服务器默认使用无状态 session，并关闭 HTTP Basic；迁移期如需 Basic，必须显式设置 `chaos.security.http-basic-enabled=true`。
- 身份只来自认证结果：匿名路径不要依赖 `RequestContext.userId()`/`tenantId()` 做授权判断，服务不应绕过网关直接暴露。
- `ChaosSecurityAutoConfiguration` 显式先于 Spring Boot `SecurityAutoConfiguration` 执行，保证资源服务器过滤器链优先于 Boot 默认链注册。
- 默认匿名白名单只包含 `/actuator/health`；OpenAPI、Swagger、Prometheus 或业务公开接口必须通过 `chaos.security.permit-all` 显式配置，并配合内网、管理端口或网关策略保护。
- 默认授权链包含 `RbacAuthorizationPolicy`，权限编码匹配（支持 `order:*` 通配与 `role-hierarchy` 角色继承）或 `chaos.security.access.admin-roles` 角色放行；RBAC 未命中时弃权，全部策略弃权时由 `AuthorizationManager` 默认拒绝。
- 配置里的 ABAC 策略被打包成一条组合策略，组内按 `chaos.security.access.combining-algorithm` 合并；组合策略与 RBAC 之间恒为拒绝优先。
- 因此细粒度限制要写成 **DENY 策略**（"不是本人就拒绝"），写成 ALLOW 在默认算法下无效——RBAC 已经放行。
- 策略配置在启动时全部校验，写错时抛 `ChaosDiagnosticException` 并给出 `chaos.security.access.policies[n]...` 的具体路径与改法。
- ABAC 请求由主体、动作、资源和环境属性组成；资源 owner、租户、部门、状态等属性由业务查询后放入 `AuthorizationResource`。
- `@DataScope` 负责设置栈式 `DataScopeRequest` 上下文，具体 SQL 条件由 MyBatis 侧 `DataScopeProvider` 扩展；该请求可转换为通用 `AuthorizationRequest` 复用 ABAC/RBAC 决策。
- `DataScopeAuthorizationService` 可在数据权限 Provider 中复用 `AuthorizationManager`，用于判断当前主体是否允许使用某个数据范围策略。
- JWT 模式必须配置资源服务器 `jwk-set-uri` 或等价 JWT 解码器。
- opaque 模式必须配置 introspection URI 和客户端凭证。
- 引入 `chaos-redis-starter` 后会自动注册 Redis JWT 黑名单撤销服务；Redis 自动装配显式先于安全自动装配执行，避免默认的 `NoopJwtRevocationService` 抢先注册。
- 授权中心启用 JWT 互踢时，资源服务器必须开启撤销检查并共享同一个 Redis 黑名单。
- 授权中心调用 `/oauth2/revoke` 撤销 access token 后，资源服务器会通过同一黑名单立即拒绝该 JWT。
