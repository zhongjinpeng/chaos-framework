# 生产检查清单：OAuth2 授权服务器

适用于 `chaos-auth-server-starter`（或 `chaos-archetype-auth-server` 生成的项目）。标注 **[启动拦截]** 的条目缺失时，
授权服务器生产安全检查会阻断启动，报错会逐项列出违规内容；其余条目需要人工确认。

配置参考：[templates/auth-server/application-prod.yml](../templates/auth-server/application-prod.yml)。

## 1. 身份适配

- [ ] 已实现 `ChaosAuthorizationUserService` 对接用户中心（密码策略、账号状态、租户、角色与权限）。
- [ ] archetype 生成的 `DevIdentityConfiguration`（演示账号）已删除；它在 `prod`/`production`/`prd` profile 下不会加载，
      未提供实现时框架默认拒绝所有登录。
- [ ] 启用短信登录时提供真实的 `SmsCodeVerifier`。

## 2. 生产模式识别

- [ ] 以 `prod`、`production` 或 `prd` profile 启动（与全局 `chaos.production-safety.profiles` 默认值一致）。
- [ ] 没有设置 `chaos.authorization.production-safety.fail-fast=false` 或任何 `allow-*` 放行开关。

## 3. 签发配置

- [ ] **[启动拦截]** `chaos.authorization.issuer` 为对外可访问地址，不是 localhost，且与网关 / 业务服务配置的 issuer 一致。
- [ ] **[启动拦截]** 客户端密钥不是 `{noop}` 明文，使用 `{bcrypt}` 等格式并从 Secret 注入。
- [ ] **[启动拦截]** 配置固定 JWK 密钥对（`jwk.key-id`、`private-key-location`、`public-key-location`）；
      轮换时把旧公钥放入 `jwk.previous-public-keys`，保证轮换期间已签发 token 仍可验签。
- [ ] token 有效期（`access-token-ttl`、`refresh-token-ttl`）已按安全要求评估。

## 4. 存储与撤销

- [ ] **[启动拦截]** 授权记录使用持久化存储（模板为 Redis 引用 token；JWT 模式使用 JDBC 存储），不是内存实现。
- [ ] **[启动拦截]** token 黑名单为 Redis 实现，不是 `NoopJwtRevocationService`；资源服务 / 网关与授权服务器共用同一 Redis 黑名单 key。
- [ ] **[启动拦截]** 启用踢人下线时，会话注册表为 Redis 实现。
- [ ] **[启动拦截]** 提供有效的审计发布器（引入 `chaos-audit-jdbc-starter` 可落库）。

## 5. 暴力破解防护

- [ ] `chaos.authorization.login-lock` 保持启用，`max-failures` / `lock-duration` 已评估。
- [ ] 网关对 `/oauth2/token` 限流（不要把它加入 `rate-limit.skip-paths`）。

## 6. 可观测性与验证

- [ ] 启动报告无 WARN 诊断；`/actuator/chaos` 未暴露到公网。
- [ ] 验证：登录 → 访问业务接口 → 注销 → 同一 token 立即失效；连续输错密码触发锁定。

参考：[chaos-authorization](../modules/chaos-authorization.md) · [启动诊断](../diagnostics.md) · [配置参考](../configuration-reference.md)
