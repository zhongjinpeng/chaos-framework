# chaos-examples

## 职责

提供可运行的三服务落地样板：

```text
example-auth-server    OAuth2 授权服务器，提供 password、sms_code 登录，支持 JWT token 和 Redis/reference token profile
example-gateway        Spring Cloud Gateway 入口，提供鉴权、租户校验、限流、黑名单、trace 透传
example-order-service  订单资源服务，演示 Web、Security、MyBatis Plus、租户、数据权限、事务、Prometheus
```

示例工程只依赖 starter，不直接依赖 autoconfigure。

## 启动顺序

前置条件：

- 本机 `localhost:6379` 有可用 Redis。`example-order-service` 引入 `chaos-redis-starter`，Redisson 启动即连接，JWT 链路也需要。
- 不需要 Nacos：`example-order-service` 默认关闭 Nacos discovery/config；需要时激活 `nacos` profile，并用 `NACOS_SERVER_ADDR` 指定地址。
- 示例配置中的 `{noop}chaos-secret`、演示账号等仅限本地使用；以 `prod`/`production`/`prd` profile 启动时生产安全检查会直接阻止启动。

JWT 链路：

```bash
./mvnw -pl chaos-examples/example-auth-server -am spring-boot:run
./mvnw -pl chaos-examples/example-order-service -am spring-boot:run -Dspring-boot.run.profiles=jwt-token
./mvnw -pl chaos-examples/example-gateway -am spring-boot:run
```

需要 H2 控制台时，为 order-service 追加 `dev` profile（如 `jwt-token,dev`）。

Redis/reference token 链路（order-service 的 introspection 密钥从环境变量读取）：

```bash
export CHAOS_EXAMPLE_OPAQUE_CLIENT_SECRET=chaos-secret
./mvnw -pl chaos-examples/example-auth-server -am spring-boot:run -Dspring-boot.run.profiles=redis-token
./mvnw -pl chaos-examples/example-order-service -am spring-boot:run -Dspring-boot.run.profiles=redis-token
./mvnw -pl chaos-examples/example-gateway -am spring-boot:run -Dspring-boot.run.profiles=redis-token
```

自动化 smoke 见 [CI And Smoke](../ci-and-smoke.md)。

## JWT 登录示例

用户名密码登录：

```bash
curl -u chaos-client:chaos-secret \
  -H 'X-Device-Id: web-001' \
  -d 'grant_type=password&username=admin&password=123456&scope=read write' \
  http://localhost:9000/oauth2/token
```

手机号验证码登录：

```bash
curl -u chaos-client:chaos-secret \
  -d 'grant_type=sms_code&mobile=13800000000&code=123456&device_id=ios-001&scope=read' \
  http://localhost:9000/oauth2/token
```

访问订单服务：

```bash
curl -H "Authorization: Bearer ${access_token}" \
  -H 'X-Tenant-Id: tenant-a' \
  http://localhost:8080/api/orders
```

创建订单：

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer ${access_token}" \
  -H 'X-Tenant-Id: tenant-a' \
  -H 'Idempotency-Key: order-demo-001' \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"A-20260524-1001","buyerId":"10001","amount":199.90}'
```

## Redis Token 和互踢

启用 `redis-token` profile 后，授权服务器签发 reference token，Gateway 和资源服务通过 `/oauth2/introspect` 校验 token。

示例配置：

```yaml
chaos:
  authorization:
    token:
      type: redis
    kickout:
      enabled: true
      scope: global
      session-registry-type: redis
```

同一个用户再次登录后，旧授权会被移除；reference token 经 introspection 会立即失效。

## Prometheus

三服务均暴露 Prometheus 指标：

```text
http://localhost:9000/actuator/prometheus
http://localhost:8081/actuator/prometheus
http://localhost:8080/actuator/prometheus
```

## Smoke 脚本

JWT 示例链路可以通过脚本自动验证：

```bash
scripts/smoke-examples-jwt.sh
```

脚本会启动 auth-server、order-service 和 gateway，完成 password grant 登录、Gateway 查询订单、Gateway 创建订单和 Prometheus 指标检查。日志写入：

```text
target/smoke-logs
```

## 示例账号

```text
admin     / 123456 / 13800000000 / tenant-a / order:read,order:create,order:cancel
operator  / 123456 / 13900000000 / tenant-b / order:read
```

## 注意事项

- 默认 JWT 链路关闭互踢，避免没有共享黑名单时产生“授权服务器已互踢、资源服务仍接受旧 JWT”的误解。
- Redis/reference token 链路使用 opaque introspection，适合演示立即撤销和全局互踢。
- 订单服务使用 H2 内存库和 MyBatis Plus 租户拦截器；真实业务应替换为 MySQL、PostgreSQL 或企业数据库。
- 生产环境必须配置固定 RSA PEM 密钥，示例里的临时 JWK 只适合本地开发。
- Gateway 白名单只包含健康检查、Prometheus 和订单 ping 接口。
