# Chaos Gateway

基于 Chaos Framework `chaos-gateway-starter` 生成的 API 网关（Spring Cloud Gateway / WebFlux）。

## 本地运行

```bash
mvn verify                 # 离线即可通过的冒烟测试
mvn spring-boot:run        # 默认 8080，日志中会打印 Chaos 启动报告
```

`application.yml` 默认：

- 把 `/api/**` 转发到 `http://localhost:8081`（可用 `chaos-archetype-web-service` 生成下游服务，启动时加 `--server.port=8081`）；
- 从 `http://localhost:9000` 获取 JWK 并校验 `iss`、`aud`（可用 `chaos-archetype-auth-server` 生成授权服务器）；
- 只放行 `/actuator/health/**`，其余路由都需要 `Authorization: Bearer <token>`。

网关默认会：剥离客户端伪造的 `X-User-Id` / `X-Tenant-Id`、拒绝含 `..`、`%2f`、`;` 的歧义路径、按路由/租户/用户/IP 限流。

## 上线前

1. 追加 `chaos-redis-starter`：多实例必须使用 Redis 集群限流与 token 黑名单，内存限流在 `prod` profile 下会阻断启动。
2. 配置 `chaos.gateway.trusted-proxies` 为 SLB / Ingress 地址段，否则黑名单和按 IP 限流看到的都是代理 IP。
3. 按 `application-prod.yml` 注入环境变量，以 `prod` profile 启动。
4. 对照 Chaos 文档中的“网关生产检查清单”逐项确认。
