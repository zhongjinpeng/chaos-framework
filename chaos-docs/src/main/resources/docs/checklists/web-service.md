# 生产检查清单：Servlet 业务服务

适用于 `chaos-web-service-starter`（或 `chaos-archetype-web-service` 生成的项目）。标注 **[启动拦截]** 的条目缺失时，
生产安全检查会直接阻断启动并在报错中给出修复方法；其余条目不会被自动发现，需要人工确认。

配置参考：[templates/web-service/application-prod.yml](../templates/web-service/application-prod.yml)。

## 1. 依赖

- [ ] **[启动拦截]** 已引入 `chaos-redis-starter`：生产 profile 下 `InMemoryRateLimiter`、`InMemoryIdempotentRepository`、
      `NoopJwtRevocationService` 不允许使用。
- [ ] 需要数据库时引入 `chaos-mybatis-starter`，并删除内存仓储实现。
- [ ] 没有同时引入 `chaos-gateway-starter`（Servlet 与 WebFlux 混用会 **[启动拦截]**）。

## 2. 生产模式识别

- [ ] 以 `prod`、`production` 或 `prd` profile 启动（或显式 `chaos.production-safety.production-mode=true`），
      否则生产安全检查不会生效。其他 profile 名需要配置 `chaos.production-safety.profiles`。
- [ ] 没有设置 `chaos.production-safety.fail-fast=false` 或 `allow-unsafe-defaults=true`
      （启动报告会以 WARN 提示“生产安全检查已放宽”）。

## 3. 身份与 token

- [ ] token 类型与授权服务器一致：引用 token 用 `chaos.security.token.type=opaque` + `opaque-token.*`；
      JWT 用 `spring.security.oauth2.resourceserver.jwt.issuer-uri`。
- [ ] opaque 模式已配置 `client-id` / `client-secret`（缺失 **[启动拦截]**），密钥来自 Secret 或配置中心。
- [ ] 评估 `chaos.security.opaque-token.cache-ttl`（默认 30s）：它是 token 撤销后仍可能被接受的最长时间。
- [ ] `chaos.security.permit-all` 只包含真正公开的路径（健康检查、文档如需公开）。
- [ ] 业务代码没有从请求头读取用户/租户；`chaos.web.forwarding.trust-identity-headers` 保持 `false`，
      除非服务只能经网关访问且已配置可信代理。

## 4. 网络与代理

- [ ] `chaos.web.forwarding.trusted-proxies` 配置为网关 / LB / Ingress 地址段，否则按 IP 限流与访问日志拿到的是代理 IP。
- [ ] 服务不对公网直接暴露，只允许经网关访问。

## 5. 数据与多租户（使用 chaos-mybatis 时）

- [ ] 业务表包含租户列（默认 `tenant_id`），`chaos.mybatis.tenant.enabled=true`。
- [ ] 保持 `missing-tenant-behavior=DENY`；定时任务、MQ 消费线程通过 `DistributedJobRunner`、`ContextRestoringMessageConsumer`
      建立租户上下文，而不是改成放行。
- [ ] 评估分页上限 `chaos.mybatis.pagination.max-limit`（默认 500）。

## 6. Redis 与多服务共用

- [ ] `chaos.redis.key-prefix` 已设置（模板默认 `${spring.application.name}`），避免多服务共用 Redis 时 key 冲突
      （为空时启动报告会提示）。

## 7. 可观测性

- [ ] 启动日志中的 Chaos 启动报告与预期一致（启用的能力、限流/幂等/撤销实现类均为 Redis 版）。
- [ ] `/actuator/chaos` 未暴露到公网（默认不暴露，如开启需加鉴权）。
- [ ] Prometheus 指标已接入监控，Redis 故障有告警。

## 8. 上线验证

- [ ] 用真实 token 调用一个写接口，确认审计事件、Trace ID（响应头与日志）正确。
- [ ] 注销后 token 立即失效（opaque 模式在 `cache-ttl` 内失效）。

参考：[启动诊断](../diagnostics.md) · [配置索引](../configuration-index.md) · [配置参考](../configuration-reference.md)
