# 5 分钟上手

目标：从零得到一个能跑、能测、能看到 Chaos 启动报告的业务服务，并知道下一步该加什么。

前置：JDK 21、Maven 3.9+。以下以 chaos `1.0.0` 为例。

## 方式一：用 archetype 生成（推荐）

```bash
mvn archetype:generate -B \
  -DarchetypeGroupId=com.michael \
  -DarchetypeArtifactId=chaos-archetype-web-service \
  -DarchetypeVersion=1.0.0 \
  -DgroupId=com.acme -DartifactId=todo-service -Dpackage=com.acme.todo
cd todo-service
```

| 我要搭建 | archetype |
| --- | --- |
| Servlet 业务服务 | `chaos-archetype-web-service` |
| API 网关 | `chaos-archetype-gateway` |
| OAuth2 授权服务器 | `chaos-archetype-auth-server` |

生成的项目已经包含：

- `chaos-boot-parent` 作为 parent（依赖版本、Java 21、打包约定都不用再配）；
- 对应的场景 starter；
- 开发 / 生产两份配置（内容与 [配置模板](templates/README.md) 一致）；
- DDD 分层示例（web-service）与一个离线即可通过的冒烟测试；
- README（本地运行、上线前要做什么）。

## 方式二：已有项目手工接入

```xml
<parent>
    <groupId>com.michael</groupId>
    <artifactId>chaos-boot-parent</artifactId>
    <version>1.0.0</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>com.michael</groupId>
        <artifactId>chaos-web-service-starter</artifactId>
    </dependency>
</dependencies>
```

已有公司级 parent 时，改为在 `dependencyManagement` 中 import `com.michael:chaos-dependencies:1.0.0`（type pom、scope import），
并参考 [chaos-boot-parent](modules/chaos-boot-parent.md) 补齐 `-parameters` 与打包插件。
配置从 [templates/web-service/application.yml](templates/web-service/application.yml) 复制。

## 第一步：跑测试

```bash
mvn verify
```

不需要授权服务器、数据库或 Redis。web-service 的测试用 `chaos-test-support` 直接模拟登录用户，
覆盖“未登录 401 / 有权限创建与查询 / 无权限 403”三种情况。

## 第二步：启动，看启动报告

```bash
mvn spring-boot:run
```

日志中会出现类似内容（说明哪些能力已启用、用的是什么实现、有什么需要注意）：

```text
Chaos 启动报告 | 应用 todo-service | profile [default] | 生产模式 否 | fail-fast 开
  已启用（5）
    web              rate-limiter=InMemoryRateLimiter, idempotent-repository=InMemoryIdempotentRepository, trusted-proxies=0, ...
    application
    tenant           servlet-filter=false
    audit            publisher=LoggingAuditEventPublisher
    security         token.type=JWT, jwk-set-uri=http://localhost:9000, ...
  未启用
    缺少依赖         audit-jdbc, security-redis, ..., mybatis, redis, mq, job, storage
  诊断（1）
    [INFO] production-safety：当前使用开发用实现 InMemoryRateLimiter、InMemoryIdempotentRepository、NoopJwtRevocationService，以生产 profile 启动时会被生产安全检查阻断
           怎么修：上线前引入 chaos-redis-starter 并配置 spring.data.redis.*，Redis 实现会自动替换这些兜底实现
```

报告各字段含义与 `/actuator/chaos` 端点见 [启动诊断](diagnostics.md)。

## 第三步：调用接口

业务接口需要授权服务器签发的 token。最快的方式是再生成一个授权服务器：

```bash
mvn archetype:generate -B -DarchetypeGroupId=com.michael -DarchetypeArtifactId=chaos-archetype-auth-server \
  -DarchetypeVersion=1.0.0 -DgroupId=com.acme -DartifactId=auth-server -Dpackage=com.acme.auth
cd auth-server && mvn spring-boot:run          # 需要本机 Redis（localhost:6379）

curl -u chaos-client:chaos-secret \
     -d 'grant_type=password&username=admin&password=admin123&scope=read write' \
     http://localhost:9000/oauth2/token
```

用返回的 `access_token` 调用业务服务：

```bash
curl -X POST http://localhost:8080/api/todos \
     -H "Authorization: Bearer <access_token>" -H "Idempotency-Key: first" \
     -H "Content-Type: application/json" -d '{"title":"hello chaos"}'
curl http://localhost:8080/api/todos -H "Authorization: Bearer <access_token>"
```

响应被统一包装为 `{"code":"0","message":"success","data":...,"traceId":"..."}`。

## 下一步

| 还需要 | 做什么 |
| --- | --- |
| 数据库 / 多租户 SQL 隔离 | 追加 `chaos-mybatis-starter`，配置见 [addons/mybatis.yml](templates/addons/mybatis.yml) |
| 上线（多实例） | 追加 `chaos-redis-starter`，按 [业务服务生产检查清单](checklists/web-service.md) 逐项确认 |
| 可靠消息 | 追加 `chaos-mq-starter`，先执行 outbox 建表脚本，见 [chaos-mq-jdbc](modules/chaos-mq-jdbc.md) |
| 统一入口 | 用 `chaos-archetype-gateway` 生成网关，见 [网关生产检查清单](checklists/gateway.md) |
| 所有配置项 | [配置参考（自动生成）](configuration-reference.md) |
| 该选哪些 starter | [Starter 选型](modules/chaos-starters.md) |
