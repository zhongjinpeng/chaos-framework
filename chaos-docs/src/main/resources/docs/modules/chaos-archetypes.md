# chaos-archetypes

## 职责

为每个场景 starter 提供 Maven archetype，一条命令生成可直接运行、可直接测试的项目：

| archetype | 生成内容 | 冒烟测试 |
| --- | --- | --- |
| `chaos-archetype-web-service` | `chaos-web-service-starter` + DDD 分层示例（interfaces / application / domain / infrastructure），内存仓储无需数据库 | 未登录 401；有权限创建与查询；无权限 403 |
| `chaos-archetype-gateway` | `chaos-gateway-starter` + `/api/**` 路由示例 | 健康检查公开；未携带 token 访问路由 401 |
| `chaos-archetype-auth-server` | `chaos-auth-server-starter` + 仅限开发环境的演示账号 | JWK Set 与授权服务器元数据可访问 |

所有生成项目：

- 以 `com.michael:chaos-boot-parent` 为 parent，版本等于生成时使用的 archetype 版本（`chaosVersion`，可覆盖）；
- 包含 `application.yml`（开发）与 `application-prod.yml`（生产），内容与 [配置模板](../templates/README.md) 一致；
- 包含中文 README（本地运行、调用示例、上线前清单）。

archetype 不是运行时依赖，不进入 `chaos-dependencies` BOM。

## 使用

```bash
mvn archetype:generate -B \
  -DarchetypeGroupId=com.michael \
  -DarchetypeArtifactId=chaos-archetype-web-service \
  -DarchetypeVersion=1.0.0 \
  -DgroupId=com.acme -DartifactId=todo-service -Dpackage=com.acme.todo
```

可选参数：`-Dversion`（默认 `0.1.0-SNAPSHOT`）、`-DchaosVersion`（默认等于 archetype 版本）。

## 为什么离线也能通过测试

| 场景 | 原因 |
| --- | --- |
| web-service | JWT 解码器只在首次解析 token 时访问 `jwk-set-uri`；测试用 `chaos-test-support` 的 `ChaosMockMvcSecurity.loginUser` 注入登录用户，仍经过完整的安全过滤器链、`@Permission` 切面、租户上下文与统一响应包装 |
| gateway | JWT 解码器懒加载；未带 token 的请求在网关鉴权过滤器被拒绝，不会连接下游 |
| auth-server | Redis 连接工厂首次读写才连接；开发环境 JWK 启动时临时生成，客户端与授权记录保存在内存。登录会用到 Redis 登录失败计数，因此测试不调用 `/oauth2/token` |

## 模板一致性（架构测试校验）

`ArchetypeArchitectureTest` 保证：

- 生成项目的 `application-prod.yml` 与 `docs/templates/<场景>/application-prod.yml` 完全一致；
  `application.yml` 除 `spring.application.name`（替换为 `${artifactId}`）外一致。模板本身由 `ConfigurationTemplatesTest` 校验配置项真实存在；
- 生成项目使用 `chaos-boot-parent` 与 `chaosVersion`，且 `chaosVersion` 默认值在构建时替换为当前版本；
- 经过 Velocity 渲染的模板中不出现 `##`（会被当作注释删除）和非 archetype 变量的 `${...}`（会被误解析）。
  需要保留这类内容的文件（`application-prod.yml`、`README.md`）在 `archetype-metadata.xml` 中声明为 `filtered="false"`。

## 端到端验证

```bash
scripts/verify-archetypes.sh
```

脚本会：

1. `./mvnw install -DskipTests`：**把当前版本的全部 chaos 构件安装到本机 `~/.m2`**（生成的独立项目只能从本地仓库解析 chaos 坐标）；
2. 用本地构建的 archetype 分别生成三个项目到 `target/archetype-it/`；
3. 在每个生成项目中放置空 `.mvn` 目录（避免继承本仓库的 `.mvn/maven.config`），执行 `mvn verify`。

环境变量：`ARCHETYPE_SKIP_INSTALL=true` 跳过安装、`ARCHETYPE_OFFLINE=true` 离线构建、`ARCHETYPES="web-service"` 只验证部分、
`ARCHETYPE_IT_DIR` 自定义输出目录。CI 的 `archetypes` job 执行同一脚本。

## 修改 archetype 时

- 改配置：先改 `chaos-docs/src/main/resources/docs/templates/`，再同步到 archetype（架构测试会指出不一致）。
- 改 Java 模板：包名使用 `${package}`；不要在 Java 中写 `@Value("${...}")`，改用 `Environment` 或 `@ConfigurationProperties`。
- 本地快速验证单个 archetype：`ARCHETYPE_SKIP_INSTALL=true ARCHETYPES=web-service scripts/verify-archetypes.sh`
  （前提是已 install 过其余模块，且只改动了 archetype 本身时需先 `./mvnw -pl chaos-archetypes/chaos-archetype-web-service install`）。
