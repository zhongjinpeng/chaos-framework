# chaos-authorization

## 职责

OAuth2 Authorization Server，多 grant_type 登录，JWT/Redis token，互踢。源码目录为 `chaos-security/chaos-authorization`，对外 artifactId 为 `chaos-authorization`。

## 依赖方式

引入 chaos-authorization-starter。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-authorization-starter</artifactId>
</dependency>
```

如果只扩展授权核心接口，可以直接依赖 `chaos-authorization`。

## 配置

```yaml
chaos:
  authorization:
    issuer: http://auth-server:9000
    access-token-ttl: 2h
    refresh-token-ttl: 30d
    reuse-refresh-tokens: false
    refresh-token:
      security-enabled: true
      audit-replay-enabled: true
    token:
      type: jwt
    client:
      store-type: memory
      id: chaos-client
      secret: "{noop}chaos-secret"
      scopes:
        - read
        - write
    jwk:
      public-key-location: classpath:keys/auth-public.pem
      private-key-location: classpath:keys/auth-private.pem
      key-id: auth-key-1
      # 密钥轮换时保留的旧公钥，只用于 JWKS 发布和验签，保留到旧 token 全部过期
      previous-public-keys:
        - key-id: auth-key-0
          public-key-location: classpath:keys/auth-public-old.pem
    # 用户名密码登录失败锁定（租户 + 用户名维度）
    login-lock:
      enabled: true
      max-failures: 5
      lock-duration: 15m
      redis-key-prefix: chaos:authorization:login-failure
      max-local-entries: 100000
    kickout:
      enabled: false
      scope: client
      session-registry-type: auto
      redis-key-prefix: chaos:authorization:kickout
      max-local-sessions: 10000
    grant:
      default-password-enabled: true
      default-sms-enabled: true
    captcha:
      enabled: false
      path: /api/v1/auth/captcha
      ttl: 2m
      length: 4
      width: 128
      height: 44
      redis-key-prefix: chaos:authorization:captcha
    production-safety:
      enabled: true
      # 发现违规项时阻止启动；迁移期可临时设为 false 只告警
      fail-fast: true
      profiles:
        - prod
        - production
      allow-memory-client-store: false
      allow-memory-authorization-store: false
      allow-memory-session-registry: false
      allow-generated-jwk: false
      allow-noop-client-secret: false
      allow-localhost-issuer: false
      allow-noop-jwt-revocation-service: false
      allow-noop-kickout-service: false
      allow-noop-audit-publisher: false
```

`token.type=jwt` 使用自包含 JWT；`token.type=redis` 使用引用 token，授权信息存储在 Redis。
启用互踢后，JWT 模式会在签发新 token 前撤销旧 access token，并通过 `JwtRevocationService` 写入黑名单。
资源服务器需要开启 `chaos.security.jwt.revocation-check-enabled=true` 才能立即拒绝旧 JWT。

Refresh token 安全增强默认开启：

```text
security-enabled：启用 refresh token 安全增强。
audit-replay-enabled：refresh token 找不到且官方校验返回 invalid_grant 时，记录重放嫌疑审计。
```

当 `reuse-refresh-tokens=false` 时，Spring Authorization Server 会轮换 refresh token。Redis token 模式会在保存新授权前清理同一授权 ID 下旧 access/refresh token 索引，避免旧 refresh token 继续反查到授权对象。

JDBC 客户端和授权存储：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/chaos_auth
    username: root
    password: root

chaos:
  authorization:
    client:
      store-type: jdbc
    token:
      type: jwt
```

启用 `client.store-type=jdbc` 后自动使用 Spring Authorization Server 官方 JDBC 实现：

```text
JdbcRegisteredClientRepository
JdbcOAuth2AuthorizationService
JdbcOAuth2AuthorizationConsentService
```

数据库表结构使用官方 schema：

```text
org/springframework/security/oauth2/server/authorization/client/oauth2-registered-client-schema.sql
org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql
org/springframework/security/oauth2/server/authorization/oauth2-authorization-consent-schema.sql
```

## 示例

```java
@Bean
ChaosAuthorizationUserService authorizationUserService() {
    return new ChaosAuthorizationUserService() {
        @Override
        public LoginUser authenticateByUsername(String username, String password) {
            return loadAndVerifyUser(username, password);
        }

        @Override
        public LoginUser loadByMobile(String mobile) {
            return loadUserByMobile(mobile);
        }
    };
}

@Bean
SmsCodeVerifier smsCodeVerifier() {
    return (mobile, code) -> verifySmsCode(mobile, code);
}
```

## 图形验证码

开启后，框架通过 JDK AWT 在后端生成 PNG 图片，并使用 Redis 保存带 TTL 的答案摘要：

```yaml
chaos:
  authorization:
    captcha:
      enabled: true
```

验证码接口默认为 `GET /api/v1/auth/captcha`，返回 `captchaId`、PNG `imageData` 和
`expiresIn`。业务服务需将该路径加入匿名访问白名单。password grant 同时提交
`captcha_id` 与 `captcha_code`；验证码在首次校验时原子删除，输入错误也必须重新获取，
refresh token grant 不受影响。

```bash
curl http://auth-server:9000/api/v1/auth/captcha

curl -u chaos-client:chaos-secret \
  -d 'grant_type=password&username=admin&password=123456&captcha_id=...&captcha_code=ABCD' \
  http://auth-server:9000/oauth2/token
```

应用可覆盖 `CaptchaImageGenerator`、`CaptchaStore` 或 `CaptchaService` Bean，替换图片算法、
存储和风控策略。验证码原文和答案摘要均不得进入访问日志或审计属性。

自定义 grantType：

```java
@Bean
ChaosGrantAuthenticationHandler socialGrantHandler() {
    return new ChaosGrantAuthenticationHandler() {
        @Override
        public AuthorizationGrantType grantType() {
            return new AuthorizationGrantType("social");
        }

        @Override
        public LoginUser authenticate(Map<String, Object> parameters) {
            return loadSocialUser((String) parameters.get("provider"), (String) parameters.get("openId"));
        }
    };
}
```

Redis token 和互踢：

```yaml
chaos:
  authorization:
    token:
      type: redis
      redis-key-prefix: chaos:authorization
    kickout:
      enabled: true
      scope: global
```

JWT token 和跨实例互踢：

```yaml
spring:
  data:
    redis:
      host: redis.internal
      port: 6379

chaos:
  authorization:
    token:
      type: jwt
    kickout:
      enabled: true
      scope: client
      session-registry-type: redis
      redis-key-prefix: chaos:authorization:kickout
```

资源服务器配置：

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://auth-server:9000/oauth2/jwks

chaos:
  security:
    jwt:
      revocation-check-enabled: true
```

互踢范围：

```text
client：同一用户在同一个 OAuth2 client 下只保留最新登录。
device：同一用户在同一个 OAuth2 client 和同一设备下只保留最新登录；未传 device_id 时降级为 client。
global：同一用户所有 OAuth2 client 只保留最新登录。
```

授权会话索引：

```text
auto：存在 RedisTemplate 时使用 Redis，否则使用本地内存。
memory：本地内存索引，仅适合单实例开发和测试。
redis：Redis 索引，适合生产多实例授权服务器。
```

JWT 互踢索引会保存授权 ID、用户、客户端、设备 ID、IP、User-Agent、grant_type、access token 撤销标识和过期时间。
旧授权对象已过期或不在当前实例内存中时，授权中心仍会按索引中的 token 标识写入黑名单并清理索引。

## 生产安全检查

授权服务器默认启用生产安全检查。命中 `prod`、`production` 或 `prd` profile（与全局 `chaos.production-safety.profiles` 默认值一致；或 `production-mode=true`）时，
如果仍使用本地开发默认值，**默认阻止应用启动**（`production-safety.fail-fast=true`）。这些默认值上线后分别意味着
公开的客户端凭据、每次重启全员掉线、注销和互踢不生效，不能只停留在 WARN 日志里：

```text
issuer 使用 localhost/127.0.0.1
client.secret 使用 {noop} 明文密钥
实际装配 InMemoryRegisteredClientRepository
实际装配 InMemoryOAuth2AuthorizationService 或 InMemoryOAuth2AuthorizationConsentService
实际装配 NoopJwtRevocationService
实际未装配 AuditEventPublisher 或装配 NoopAuditEventPublisher
启用 kickout 后仍使用 InMemoryAuthorizationSessionRegistry 或 NoopAuthorizationKickoutService
未配置固定 public/private JWK，依赖启动期临时生成密钥
```

以下两项只输出告警，不阻止启动（重启丢失动态注册客户端，但不影响已配置客户端签发 token）：

```text
client.store-type 使用 memory / 实际装配 InMemoryRegisteredClientRepository
实际装配 InMemoryOAuth2AuthorizationConsentService
```

生产推荐配置：

```yaml
spring:
  profiles:
    active: prod

chaos:
  authorization:
    issuer: https://auth.example.com
    client:
      store-type: jdbc
    jwk:
      public-key-location: /etc/chaos/keys/auth-public.pem
      private-key-location: /etc/chaos/keys/auth-private.pem
      key-id: auth-key-2026-01
```

如果企业使用 `prd`、`online` 等非标准生产 profile，需要显式配置：

```yaml
chaos:
  authorization:
    production-safety:
      profiles:
        - prd
        - online
```

违规项默认阻止启动。不建议长期放宽 `allow-*` 开关，也不建议长期设置 `fail-fast=false`。
若必须分阶段上线，可只临时放宽对应项，例如 `allow-memory-authorization-store=true` 或
`allow-noop-jwt-revocation-service=true`；替换为 JDBC、Redis、真实审计发布器后应删除放行配置。

## 退出登录

`POST /api/v1/auth/logout`（路径由 `chaos.authorization.logout.path` 配置）携带 `Authorization: Bearer <access_token>`，
框架依次执行：

1. 通过 `JwtRevocationService` 把 access token 写入撤销黑名单（TTL 为 token 剩余有效期）；
2. 从互踢会话索引中移除该授权；
3. 从 `OAuth2AuthorizationService` 删除授权记录；
4. 发布 `auth.token.revoke` 审计事件。

JWT 自包含 token 在资源服务器本地验签，只删除授权记录不会让 token 失效，因此资源服务器必须开启
`chaos.security.jwt.revocation-check-enabled=true` 并与授权服务器共享同一份 Redis 黑名单。

## 登录失败锁定

默认用户名密码 grant 按"租户 + 用户名（忽略大小写）"统计连续认证失败次数：

- 达到 `login-lock.max-failures` 后，在 `lock-duration` 内直接返回 `invalid_grant`（`too many failed login attempts`），不再调用业务身份服务；
- 登录成功清零计数；
- 存在 `StringRedisTemplate` 时使用 `RedisLoginFailureLimiter`（Lua 原子自增 + 过期），集群共享计数；否则使用单实例内存实现；
- 注册自定义 `LoginFailureLimiter` Bean 可接入风控平台；设置 `login-lock.enabled=false` 可关闭；
- 按客户端 IP 的暴力破解防护应由网关 `chaos.gateway.rate-limit` 负责（登录接口虽然在鉴权白名单中，但仍参与限流）。

## JWK 密钥轮换

1. 生成新密钥，把当前 `public-key-location` 移到 `previous-public-keys`，并保留原 `key-id`；
2. `public-key-location`/`private-key-location`/`key-id` 指向新密钥并发布；
3. 新 token 使用新密钥签发，JWKS 端点同时发布新旧公钥，旧 token 继续可验签；
4. 至少等待一个 `access-token-ttl`（使用 refresh token 轮换时等待 refresh token 全部轮换完）后删除旧公钥。

签发时只会选择带私钥的当前密钥，旧公钥不会参与签名。

设备级互踢请求示例：

```bash
curl -u chaos-client:chaos-secret \
  -H 'X-Device-Id: web-chrome-001' \
  -H 'X-Forwarded-For: 10.0.0.10' \
  -d 'grant_type=password&username=admin&password=123456&scope=read write' \
  http://auth-server:9000/oauth2/token
```

也可以通过表单参数传递设备 ID：

```bash
curl -u chaos-client:chaos-secret \
  -d 'grant_type=sms_code&mobile=13800000000&code=123456&device_id=ios-001&scope=read' \
  http://auth-server:9000/oauth2/token
```

设备 ID 会进入授权会话索引、互踢审计和 JWT `deviceId` claim。禁止传递密码、验证码、完整 token 等敏感值作为设备 ID 或扩展参数。

标准撤销端点：

```bash
curl -X POST http://auth-server:9000/oauth2/revoke \
  -u chaos-client:chaos-secret \
  -d token="${access_token}"
```

`/oauth2/revoke` 会保留 Spring Authorization Server 官方客户端校验和授权失效逻辑，并额外把 access token 写入 `JwtRevocationService` 黑名单。

Refresh token 重放嫌疑会记录审计事件：

```text
auth.refresh.replay
```

Refresh token 刷新成功会记录审计事件：

```text
auth.refresh.success
```

## 扩展点

- 优先注册 Spring Bean 覆盖默认实现。
- 不在业务代码中依赖 autoconfigure 类。
- 不跨层调用 infra 实现，domain/application 只依赖接口。
- `client.store-type=jdbc` 可接入官方 JDBC 客户端和授权存储；覆盖 `RegisteredClientRepository` 可接入自研客户端中心。
- 覆盖 `SmsCodeVerifier` 可接入短信平台、图形验证码或风控校验。
- 覆盖 `AuthorizationSessionRegistry` 可接入自研会话中心。
- 覆盖 `JwtRevocationService` 可接入外部黑名单、网关会话中心或 introspection。

## 注意事项

- 生产环境必须配置固定 RSA PEM 密钥；未配置时自动生成临时密钥，只适合本地开发，生产 profile 下会阻止启动。
- `ChaosAuthorizationAutoConfiguration` 显式先于 `ChaosSecurityAutoConfiguration` 与 Spring Boot 默认安全自动装配执行，授权服务器端点过滤器链稳定优先注册。
- JWT 模式下已签发 access token 是自包含 token；需要立即失效时，授权服务器和资源服务器必须共享同一套 `JwtRevocationService` 存储。
- 生产环境建议保持 `reuse-refresh-tokens=false`，让 refresh token 每次使用后轮换。
- 生产多实例授权服务器建议设置 `kickout.session-registry-type=redis`。
- 本地内存授权会话索引默认最多保留 `10000` 个会话，超过后拒绝记录新会话，避免高基数登录无限占用内存。
- 自定义 grantType 时，客户端 `RegisteredClient` 也必须声明允许对应 grantType。
- JDBC 模式不会自动初始化表结构，建议使用 Flyway/Liquibase 管理官方 schema。
- 需要多端同时在线时建议使用 `kickout.scope=device`，由前端、App 或设备管理服务稳定生成 `device_id`。
