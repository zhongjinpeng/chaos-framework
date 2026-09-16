# Chaos Web Service

基于 Chaos Framework `chaos-web-service-starter` 生成的 Servlet 业务服务。

## 目录结构

```text
src/main/java/.../
├── Application.java
├── interfaces/rest        HTTP 接口：参数校验、DTO 转换（TodoController）
├── application            用例编排、权限声明、事件发布（TodoApplicationService）
├── domain/todo            聚合、领域事件、仓储接口，不依赖 Spring（Todo、TodoRepository）
└── infrastructure         仓储实现与外部系统适配（InMemoryTodoRepository）
```

## 本地运行

```bash
mvn verify                 # 离线即可通过的冒烟测试
mvn spring-boot:run        # 启动在 8080，日志中会打印 Chaos 启动报告
```

调用接口需要授权服务器签发的 token（`application.yml` 默认指向 `http://localhost:9000`），
可用 `chaos-archetype-auth-server` 生成一个授权服务器，登录用户需要 `todo:read` / `todo:write` 权限：

```bash
curl -H "Authorization: Bearer <access_token>" http://localhost:8080/api/todos
curl -X POST -H "Authorization: Bearer <access_token>" -H "Idempotency-Key: $(uuidgen)" \
     -H "Content-Type: application/json" -d '{"title":"hello"}' http://localhost:8080/api/todos
```

## 上线前

1. 追加 `chaos-redis-starter`（生产环境不允许内存限流、幂等和 Noop token 黑名单，否则启动失败）。
2. 按 `application-prod.yml` 注入环境变量，并以 `prod` profile 启动。
3. 如果需要数据库，追加 `chaos-mybatis-starter`，用 MyBatis 实现替换 `InMemoryTodoRepository`。
4. 对照 Chaos 文档中的“业务服务生产检查清单”逐项确认。
