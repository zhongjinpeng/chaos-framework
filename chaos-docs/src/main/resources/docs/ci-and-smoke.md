# chaos-framework CI 与 Smoke

## CI 质量门禁

仓库提供两个 GitHub Actions workflow 和一份 Dependabot 配置：

```text
.github/workflows/ci.yml       结构扫描、单测+覆盖率、发布治理、集成测试、Python、示例 smoke
.github/workflows/codeql.yml   CodeQL 安全扫描（Java、Python）
.github/dependabot.yml         Maven / GitHub Actions / pip 依赖更新
```

`ci.yml` 在 pull request、`main`、`master`、`develop` 分支 push 时执行，同一分支的新提交会取消旧流水线（`concurrency`）。

| Job | 依赖 | 命令 | 说明 |
| --- | --- | --- | --- |
| `structure` | 无 | `scripts/verify-structure.sh` | 只需 shell + ripgrep，最先执行、快速失败 |
| `build` | structure | `./mvnw -B -Pchaos-coverage verify`、`python3 scripts/generate-configuration-reference.py --check`、`./mvnw -B -Pchaos-release -Dgpg.skip=true -DskipTests validate` | 单元测试、架构测试、依赖收敛、JaCoCo 报告、配置参考文档是否最新、发布治理；失败时上传 surefire 报告 |
| `integration-test` | structure | `./mvnw -B -Pchaos-integration-test verify` | Testcontainers（Redis、MySQL、Kafka），runner 自带 Docker |
| `python` | structure | `pip install -e '.[test]' && pytest -q` | `chaos-python/chaos-tracing` |
| `archetypes` | build | `scripts/verify-archetypes.sh` | install 全部模块，用本地 archetype 生成 web-service / gateway / auth-server 项目并执行 `mvn verify` |
| `smoke` | build | `scripts/smoke-examples-jwt.sh` | 只在 `main`/`master` push 或手动触发时执行 |

CI 中所有 Maven 命令都会追加 `-gs .mvn/settings-central.xml -s .mvn/settings-central.xml`，本地复现时同样追加即可。

质量门禁覆盖：

- 单元测试和架构测试（`chaos-architecture-tests`）。
- 默认构建即执行 Maven Enforcer：JDK `[21,22)`、Maven `3.9+`、`dependencyConvergence`。
- 发布治理 validate：`requirePluginVersions`。
- 禁止 `package-info.java`、`spring.factories`。
- starter 模块禁止 Java 代码。
- 代码与配置文件中禁止 `TODO`、`FIXME`、`伪代码`、`System.out`、`printStackTrace`；Java 中禁止 `RedisUtil`。

## 配置参考文档

[Configuration Reference](configuration-reference.md) 由编译期配置元数据（`spring-configuration-metadata.json`）自动生成，
CI 的 `build` job 在编译后执行 `--check`，文档过期时失败。修改了 `@ConfigurationProperties` 字段、字段 Javadoc 或
`additional-spring-configuration-metadata.json` 后，在本地重新生成并提交：

```bash
./mvnw -B -DskipTests compile
python3 scripts/generate-configuration-reference.py
```

`docs/templates` 下的配置模板由 `chaos-autoconfigure` 中的 `ConfigurationTemplatesTest` 校验（随单元测试执行）：
配置项必须存在于配置元数据中，值必须能绑定到对应属性类。

## 本地结构扫描

```bash
scripts/verify-structure.sh
```

脚本优先使用 ripgrep，未安装时自动回退到 grep。搜索工具“无匹配”（退出码 1）视为通过，工具缺失或执行错误（退出码大于 1）会直接失败，
不会再出现“命令不存在却显示检查通过”的情况。Markdown 文档不参与占位代码扫描，因此文档中可以描述这些规则本身。

## 覆盖率

```bash
./mvnw -B -Pchaos-coverage verify
```

报告位于各模块 `target/site/jacoco/index.html`。门槛由 `chaos.coverage.minimum`（行覆盖率，默认 `0.00`）控制，
补齐测试后可以在 CI 中逐步提高，例如 `-Dchaos.coverage.minimum=0.60`。

## Archetype 验证

```bash
scripts/verify-archetypes.sh
```

脚本先执行 `./mvnw install -DskipTests`（**会写入本机 `~/.m2`**：生成的独立项目只能从本地仓库解析 chaos 坐标），
再把三个 archetype 生成到 `target/archetype-it/`，并在每个生成项目中执行 `mvn verify`（项目内放置空 `.mvn`，避免继承本仓库的 `maven.config`）。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ARCHETYPE_SKIP_INSTALL` | `false` | 已 install 当前代码时跳过安装 |
| `ARCHETYPE_OFFLINE` | `false` | 生成与验证时使用 `-o` |
| `ARCHETYPES` | `web-service gateway auth-server` | 只验证部分 archetype |
| `ARCHETYPE_IT_DIR` | `target/archetype-it` | 生成目录 |

详见 [chaos-archetypes](modules/chaos-archetypes.md)。

## 示例链路 Smoke

JWT 链路 smoke 脚本：

```bash
scripts/smoke-examples-jwt.sh
```

脚本先用 `./mvnw -pl <三个示例> -am -DskipTests package` 一次性打包，再以 `java -jar` 启动：

```text
example-auth-server
example-order-service（profile: jwt-token）
example-gateway
```

并验证：

- 三个服务的就绪检查（`/actuator/health/readiness`）。
- password grant 获取 JWT access token。
- Gateway 访问订单查询接口。
- Gateway 创建订单接口。
- 三个服务的 `/actuator/prometheus`。

可调参数：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AUTH_PORT` / `ORDER_PORT` / `GATEWAY_PORT` | `9000` / `8081` / `8080` | 服务端口 |
| `SMOKE_WAIT_ATTEMPTS` | `150` | 每个服务健康检查最大尝试次数 |
| `SMOKE_WAIT_INTERVAL` | `2` | 健康检查间隔（秒） |
| `SMOKE_SKIP_BUILD` | `false` | 已打包时跳过构建 |
| `SMOKE_HEALTH_PATH` | `/actuator/health/readiness` | 就绪检查路径；readiness 不聚合 Redis 健康指标 |

日志输出目录：

```text
target/smoke-logs
```

## 执行前提

- JDK 21。
- 使用仓库自带的 `./mvnw`（Maven 3.9.14），无需本机安装 Maven。
- 本机端口 `9000`、`8081` 未被占用；`8080` 被占用时可设置 `GATEWAY_PORT`（脚本会以 `--server.port` 传给示例）。
- 本机 `localhost:6379` 有可用 Redis：example-order-service 引入 `chaos-redis-starter`，Redisson 启动即连接。CI 的 `smoke` job 通过 `services.redis` 提供。
- 不需要 Nacos：示例默认关闭 Nacos discovery/config，需要时激活 `nacos` profile（`NACOS_SERVER_ADDR` 可覆盖地址）。
- 脚本按修改时间选择 `target` 下最新的可执行 jar，`target` 中残留的旧版本 jar 不会被误用。

Redis/reference token 链路仍按 `modules/chaos-examples.md` 手工启动。需要纳入自动 smoke 时，应先提供 Redis 容器或独立测试环境。
