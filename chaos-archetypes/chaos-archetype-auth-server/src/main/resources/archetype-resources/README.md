# Chaos Auth Server

基于 Chaos Framework `chaos-auth-server-starter` 生成的 OAuth2 授权服务器。

## 本地运行

```bash
mvn verify                 # 离线即可通过的冒烟测试
redis-server               # 登录失败锁定与 token 黑名单使用 localhost:6379
mvn spring-boot:run        # 端口 9000，日志中会打印 Chaos 启动报告
```

用开发环境演示账号（`identity/DevIdentityConfiguration`，用户名 admin / 密码 admin123，租户 tenant-a）换取 token：

```bash
curl -u chaos-client:chaos-secret \
     -d 'grant_type=password&username=admin&password=admin123&scope=read write' \
     http://localhost:9000/oauth2/token
```

签发的 JWT 可以直接访问 `chaos-archetype-gateway` 与 `chaos-archetype-web-service` 生成的项目。

## 上线前

1. 删除 `DevIdentityConfiguration`，实现 `ChaosAuthorizationUserService` 对接用户中心。
2. 配置固定 JWK 密钥对、非 localhost 的 issuer、加密格式的客户端密钥和持久化授权存储，
   任一项缺失时 `prod` profile 启动会被生产安全检查阻断（报错信息会列出缺失项和修复方法）。
3. 按 `application-prod.yml` 注入环境变量，以 `prod` profile 启动。
4. 对照 Chaos 文档中的“授权服务器生产检查清单”逐项确认。
