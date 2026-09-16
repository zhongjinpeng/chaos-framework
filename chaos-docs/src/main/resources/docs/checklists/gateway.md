# 生产检查清单：API 网关

适用于 `chaos-gateway-starter`（或 `chaos-archetype-gateway` 生成的项目）。标注 **[启动拦截]** 的条目缺失时，
启动会被阻断并给出修复方法；其余条目需要人工确认。

配置参考：[templates/gateway/application-prod.yml](../templates/gateway/application-prod.yml)。

## 1. 依赖

- [ ] **[启动拦截]** 已引入 `chaos-redis-starter`：多实例必须使用 Redis 集群限流，`InMemoryRateLimiter` 在生产 profile 下不允许使用。
- [ ] 没有引入 `chaos-web-starter` / `chaos-web-service-starter`（Servlet）。
- [ ] 使用 Nacos 动态路由时引入 `chaos-gateway-nacos-starter`。

## 2. 生产模式识别

- [ ] 以 `prod`、`production` 或 `prd` profile 启动；没有设置 `chaos.production-safety.fail-fast=false`。

## 3. 鉴权

- [ ] `chaos.gateway.auth-enabled=true`（默认）。
- [ ] JWT 模式：已配置 `chaos.gateway.jwt.jwk-set-uri`（缺失 **[启动拦截]**），并配置 `issuer-uri` 与 `audiences`
      （缺失时启动 WARN，同一授权服务器签发给其他客户端的 token 也会被接受）。
- [ ] opaque 模式：`chaos.gateway.opaque-token.client-id` / `client-secret` 已配置（缺失 **[启动拦截]**），
      评估 `cache-ttl`（撤销生效的最大延迟）与 `timeout`。
- [ ] `chaos.gateway.whitelist` 只包含真正公开的路径；登录、验证码等接口虽然在白名单中，仍参与限流。

## 4. 网络与代理

- [ ] `chaos.gateway.trusted-proxies` 配置为 SLB / Ingress 地址段，否则黑名单、按 IP 限流看到的都是代理 IP
      （未配置时启动报告会提示）。
- [ ] 保持 `chaos.gateway.reject-ambiguous-path=true`，确认业务 URL 不依赖 `..`、`;`、`%2f`。
- [ ] `chaos.gateway.internal-headers` 包含所有下游信任的内部头（默认 `X-User-Id`、`X-Tenant-Id`），新增内部头时同步追加。

## 5. 限流与租户

- [ ] `chaos.gateway.rate-limit.default-permits-per-second` 已按容量评估。
- [ ] `rate-limit.skip-paths` 只跳过健康检查等无成本路径。
- [ ] 明确 `rate-limit.fail-open` 取舍（Redis 故障时放行或拒绝），并为 Redis 配置告警。
- [ ] 多租户：保持 `chaos.gateway.tenant.fail-closed=true`；请求头租户与 token 租户不一致时返回 403，这是预期行为。

## 6. Redis

- [ ] `chaos.redis.key-prefix` 已设置，避免与业务服务共用 Redis 时 key 冲突。

## 7. 可观测性与验证

- [ ] 启动报告中限流实现为 Redis 版、`trusted-proxies` 数量正确、无 WARN 诊断。
- [ ] `/actuator/chaos` 未暴露到公网。
- [ ] 伪造 `X-User-Id` / `X-Forwarded-For` 请求验证：下游收不到伪造头，限流与黑名单按真实 IP 生效。

参考：[启动诊断](../diagnostics.md) · [chaos-gateway 安全模型](../modules/chaos-gateway.md) · [配置参考](../configuration-reference.md)
