# chaos-security-redis

## 职责

安全领域的 Redis 适配，集中在本模块而不是散落在各自动装配里：

| 包 | 内容 |
| --- | --- |
| `com.michael.chaos.security.redis.token` | `RedisJwtRevocationService`：全框架唯一的 JWT 黑名单实现 |
| `com.michael.chaos.security.redis.authorization` | 授权服务器 Redis 存储：`RedisOAuth2AuthorizationService`、`RedisRegisteredClientRepository`、`RedisOAuth2AuthorizationConsentService`、`RedisAuthorizationSessionRegistry`、`RedisAuthorizationKickoutService`、`RedisCaptchaStore`、`RedisLoginFailureLimiter` |

依赖 `chaos-security-api`；`chaos-authorization`、`spring-data-redis` 为 optional。

## JWT 黑名单统一实现

授权服务器写入、资源服务器与网关读取同一个 key：`chaos:security:jwt:blacklist:<tokenId>`（纯字符串，基于 `StringRedisTemplate`，
Lettuce 与 Redisson 连接工厂都可用）。

统一使用 `StringRedisTemplate`：若授权服务器用 `RedisTemplate<Object,Object>` 写入，key 会被 JDK 序列化，资源服务器按字符串读取永远查不到，
导致“注销后 token 仍然可用”。2.0 删除了 `RedisTemplateJwtRevocationService`，三方统一使用 `RedisJwtRevocationService`，
并由集成测试验证一个客户端写入的记录另一个客户端可见。

## 使用

- `chaos-security-starter`、`chaos-gateway-starter` 已包含本模块，引入 `chaos-redis-starter`（或 Spring Data Redis）后自动装配黑名单实现。
- 授权服务器使用 Redis 存储：`chaos-authorization-starter` + Redis，按 `chaos.authorization.client.store-type`、`chaos.authorization.token.type`、`chaos.authorization.kickout.session-registry-type` 等配置为 `redis`（完整列表见 Configuration Index）。
- 本模块不包含通用 Redis 能力；锁、幂等、限流等在 [chaos-redis](chaos-redis.md)，`chaos-redis` 不得包含安全概念（架构测试规则 4）。

更多说明见 [chaos-security](chaos-security.md)。
