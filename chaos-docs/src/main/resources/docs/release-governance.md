# chaos-framework 发布治理

## 目标

发布治理用于保证 `chaos-framework` 作为企业级基础框架被业务系统稳定引用。治理重点不是把发布流程做复杂，而是把版本兼容、依赖收敛、API 变更和 starter 依赖边界固化为可检查规则。

## 版本兼容矩阵

| chaos-framework | JDK | Spring Boot | Spring Cloud | Spring Cloud Alibaba | Maven | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| `1.0.x` | `21` | `3.5.x`（当前 `3.5.16`） | `2025.0.x`（当前 `2025.0.3`） | `2025.0.0.0` | `3.9+` | 当前开发线 |

说明：

- Spring Cloud `2025.0.x` 与 Spring Boot `3.5.x` 对齐。补丁版本（含安全修复）应及时跟进，Dependabot 会每周提醒。
- Spring Boot 4 / Spring Cloud `2025.1.x` / Spring Cloud Alibaba `2025.1.x` 属于大版本迁移，需要独立分支评估。
- 如果业务系统必须固定在 Spring Boot `3.4.x`，应切换到 Spring Cloud `2024.0.x`，并在独立兼容分支验证。
- JDK 范围由 Maven Enforcer 固定为 `[21,22)`，避免编译产物误用更高版本 API。

## 版本号管理

整个工程使用 Maven CI friendly 版本：

- 所有 POM 的版本（含 `<parent>` 中的版本）写作 `${revision}`。
- 默认值位于 `.mvn/maven.config`：`-Drevision=1.0.0`。发布新版本只需修改这一处，或临时 `-Drevision=1.0.1` 覆盖。
- `flatten-maven-plugin`（`resolveCiFriendliesOnly`）在 `process-resources` 阶段生成 `.flattened-pom.xml`，
  install/deploy 出去的 POM 中 `${revision}` 已被替换为真实版本，下游可以正常解析 parent 和 BOM。

## 依赖版本唯一来源

```text
chaos-dependencies/pom.xml   无 parent 的公共 BOM：Spring Boot/Cloud/Alibaba/Testcontainers BOM import、三方库版本、全部 chaos artifact
pom.xml (chaos-parent)       parent 为 chaos-dependencies，只维护构建插件、Enforcer、测试/覆盖率/发布 profile
```

- 升级 Spring Boot、Spring Cloud 或三方库，只修改 `chaos-dependencies/pom.xml` 的 properties。
- 模块 POM 中引用 chaos 模块或受管三方库时不写 `<version>`。
- `chaos-architecture-tests` 的架构测试会双向校验 BOM：不得缺少真实模块，也不得声明仓库中不存在的模块；根 POM 不得再声明 `dependencyManagement`。

## 发布前检查

普通本地开发执行：

```bash
./mvnw -B test
```

发布候选执行（含 API 兼容检查、source/javadoc 附件）：

```bash
./mvnw -B -Pchaos-release -Dgpg.skip=true verify
```

真实发布（Maven Central Portal）：

```bash
./mvnw -B -Pchaos-release -Dgpg.skip=false \
  -Dchaos.project.url=https://github.com/<owner>/<repo> \
  -Dchaos.scm.connection=scm:git:https://github.com/<owner>/<repo>.git \
  deploy
```

也可以使用 `mvn -Pchaos-release deploy`，效果相同。发布前提：

- `~/.m2/settings.xml` 中配置 `<server><id>central</id>` 的 Portal token。
- `chaos-dependencies/pom.xml` 中 `chaos.project.url`、`chaos.scm.connection`、`chaos.scm.developer-connection` 指向真实仓库（enforcer 在 deploy 阶段校验）。
  `chaos-release` profile 在 `deploy` 阶段用 Enforcer `requireProperty` 拦截占位值。
- `central-publishing-maven-plugin` 配置为 `autoPublish=false`，上传后需要在 Portal 上人工确认发布。
- `chaos-examples`、`chaos-architecture-tests`、`chaos-docs` 设置了 `maven.deploy.skip=true`，不会被发布。

## API 兼容检查

API 兼容检查使用 `japicmp-maven-plugin`，绑定在 `chaos-release` profile 的 `verify` 阶段，普通 `test` 不运行。

```bash
./mvnw -B -Pchaos-release \
  -Dchaos.release.compareVersion=1.0.0 \
  -Dgpg.skip=true \
  verify
```

- **1.0.0 是首个对外发布版本，没有可比基线**，因此 `chaos.api.check.skip` 暂为 `true`。
  发布 1.0.1 及之后的版本时，把 `chaos.release.compareVersion` 设为上一个已发布版本并把 skip 改回 `false`。
- 按语义化版本判定是否阻断（`breakBuildBasedOnSemanticVersioning=true`）：插件比较基线版本与当前 `revision`，
  `PATCH` 升级出现任何 API 变化、`MINOR` 升级出现不兼容变更时阻断；`MAJOR` 升级允许不兼容变更。
- 基线制品在仓库中不存在（新模块、首次发布、私服未同步）时自动跳过（`ignoreMissingOldVersion=true`），不会阻断发布。
- pom 打包模块（聚合 pom、starter）自动跳过（`skipPomModules=true`）。
- `-Dchaos.api.check.skip=true` 仍可临时跳过检查，但只应用于排查插件问题，不作为大版本发布的常规手段。

### 防止门禁空转

japicmp 被跳过时**不输出任何内容**，发布日志和真跑过一遍长得一模一样。发布到 Maven Central 不可撤销，
所以 `chaos-release` profile 在 `validate` 阶段用 enforcer 拦住两种空转：

| 情况 | 结果 |
| --- | --- |
| `revision != 1.0.0` 但 `chaos.api.check.skip` 仍为 `true` | 构建立即失败，提示改哪两个属性 |
| `chaos.release.compareVersion` 与 `revision` 相同 | 构建立即失败（拿自己和自己比，永远零差异通过） |

enforcer 管不到第三种：基线制品在仓库中不存在时 `ignoreMissingOldVersion=true` 会让检查静默消失。
发布验证跑完后用脚本确认检查真的执行过：

```bash
./mvnw -B -Pchaos-release -Dchaos.api.check.skip=false \
       -Dchaos.release.compareVersion=1.0.0 -Dgpg.skip=true verify
scripts/verify-api-compatibility.sh 1.0.0
```

脚本统计有多少个发布模块产出了兼容性报告：一个都没有直接失败并列出常见原因；有模块缺报告时列出来，
其中本就该存在于基线版本里的模块需要人工确认（说明它的基线没解析到，检查被跳过了）。

> **本地基线可能是陈旧的同名制品。** 内部迭代期间 `1.0.0` 这个版本号被复用过多次，
> `~/.m2` 里可能留着几个月前的 `chaos-*-1.0.0.jar`，与实际发布的 1.0.0 内容不同。
> 拿它当基线会得到错误结论（多报或漏报）。在本地做发布验证前先清掉：
>
> ```bash
> rm -rf ~/.m2/repository/com/michael
> ```
>
> CI 从 Maven Central 解析基线，没有这个问题。

规则：

- `PATCH` 版本不得破坏二进制兼容。
- `MINOR` 版本允许新增 API，不允许删除公开 API。
- `MAJOR` 版本允许破坏性变更，但必须在 `CHANGELOG.md` 标出迁移说明。
- `*-starter` 不承载 Java API，因此不作为业务扩展点设计。

## BOM 治理

业务系统只需要导入 `chaos-dependencies`：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.michael</groupId>
            <artifactId>chaos-dependencies</artifactId>
            <version>${chaos.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

BOM 管理：

- 所有可被业务直接引用的核心 artifact。
- 所有 starter artifact。
- `chaos-autoconfigure`（单一自动装配模块），供内部组合和高级扩展使用。
- 框架使用、但 Spring Boot / Spring Cloud BOM 未管理的三方库版本（MyBatis-Plus、Redisson、springdoc、jjwt、MinIO、OSS、RocketMQ、nimbus-jose-jwt、oauth2-oidc-sdk 等）。

BOM 不包含：`chaos-architecture-tests`（仓库内部架构测试，不发布）、`chaos-examples`、`chaos-docs`、各 `*-parent` 聚合 POM，
（BOM 只管理当前有效坐标）。

## Starter 依赖树

`chaos-autoconfigure` 中的 chaos 功能库与三方框架全部声明为 `optional`，每个 starter 显式带入 `chaos-autoconfigure`、对应功能库和所需框架。

| Starter | 聚合依赖 | 使用场景 |
| --- | --- | --- |
| `chaos-web-service-starter` | 场景 starter：`chaos-web-starter`、`chaos-security-starter`、`chaos-tenant-starter`、`chaos-audit-starter`、`chaos-application-starter` | Servlet 业务服务（推荐入口） |
| `chaos-auth-server-starter` | 场景 starter：`chaos-authorization-starter`、Actuator、Prometheus | OAuth2 授权服务器（推荐入口） |
| `chaos-web-starter` | `chaos-autoconfigure`（web）、Spring MVC、Validation、Actuator、OpenAPI | 普通 Web 服务 |
| `chaos-security-starter` | `chaos-audit-starter`、`chaos-autoconfigure`（security）、OAuth2 Resource Server、AOP、Validation | 资源服务器和 RBAC |
| `chaos-authorization-starter` | `chaos-audit-starter`、`chaos-autoconfigure`（authorization）、Web、Security、Authorization Server、Data Redis、Validation | OAuth2 授权服务器 |
| `chaos-application-starter` | `chaos-autoconfigure`（service）、Cache、AOP、Retry、Prometheus | 应用层能力（事务、重试、领域事件、上下文传播）；1.0.0 前名为 `chaos-service-starter` |
| `chaos-cloud-starter` | `chaos-autoconfigure`（cloud）、OpenFeign、LoadBalancer、Validation | Servlet 服务间 Feign 调用 |
| `chaos-cloud-reactive-starter` | `chaos-autoconfigure`（cloud）、LoadBalancer、Actuator、Validation | 响应式服务，不带 OpenFeign |
| `chaos-cloud-nacos-starter` | `chaos-autoconfigure`（cloud-nacos）、Nacos Discovery、Nacos Config | Nacos 注册与配置约定 |
| `chaos-gateway-starter` | `chaos-audit-starter`、`chaos-autoconfigure`（gateway）、Spring Cloud Gateway、Resource Server、oauth2-oidc-sdk、Actuator、Prometheus、Validation | API Gateway（同时是网关场景 starter） |
| `chaos-gateway-nacos-starter` | `chaos-gateway-starter`、`chaos-autoconfigure`（gateway-nacos）、Nacos Config、Validation | Gateway 动态路由 |
| `chaos-job-starter` | `chaos-autoconfigure`（job）、Spring Boot Starter | 定时任务服务（不再强制带入 Actuator） |
| `chaos-tenant-starter` | `chaos-autoconfigure`（tenant）、Validation | 租户治理 |
| `chaos-redis-starter` | `chaos-autoconfigure`（redis）、Redisson | Redis 基础设施能力 |
| `chaos-mybatis-starter` | `chaos-autoconfigure`（mybatis）、MyBatis Plus、JSQLParser、Validation | 数据访问服务 |
| `chaos-mq-starter` | `chaos-autoconfigure`（mq）、`chaos-mq-kafka`、`chaos-mq-rocketmq`、`chaos-mq-jdbc` | 消息服务 |
| `chaos-storage-starter` | `chaos-autoconfigure`（storage）、OSS、MinIO、Validation | 对象存储 |
| `chaos-audit-starter` | `chaos-autoconfigure`（audit） | 默认结构化日志审计 |
| `chaos-audit-jdbc-starter` | `chaos-audit-starter`、`chaos-autoconfigure`（audit-jdbc）、JDBC、Validation | JDBC 审计持久化 |

典型 Web 业务服务的组合（场景 starter + 能力 starter，选型说明见 [chaos-starters](modules/chaos-starters.md)）：

```xml
<dependency><groupId>com.michael</groupId><artifactId>chaos-web-service-starter</artifactId></dependency>
<dependency><groupId>com.michael</groupId><artifactId>chaos-redis-starter</artifactId></dependency>
```

`chaos-boot-parent`（packaging pom）作为业务应用推荐 parent 发布，以 `chaos-dependencies` 为 parent，不进入 BOM。

约束：

- starter 只做依赖聚合，不允许出现 `src/main/java`。
- 业务示例只能依赖 starter，不直接依赖 `chaos-autoconfigure`；库模块不得依赖 `chaos-autoconfigure`。
- autoconfigure 只负责条件装配，不承载业务流程。
- 承载 `@ConfigurationProperties` 的模块（无论是库模块还是 `chaos-autoconfigure`）必须引入 `spring-boot-configuration-processor`（optional）。

## Changelog 规范

每次发布必须更新仓库根目录 `CHANGELOG.md`。未发布的变更先记录在 `## Unreleased` 下，发布时改为版本号。

推荐分类：

- `Added`：新增能力。
- `Changed`：兼容性调整或默认行为变化。
- `Deprecated`：即将废弃的 API 或配置。
- `Removed`：已经移除的能力。
- `Fixed`：缺陷修复。
- `Security`：安全相关修复或治理变化。

## 发布验收清单

- `./mvnw -B test` 通过。
- `./mvnw -B -Pchaos-integration-test verify` 通过。
- `./mvnw -B -Pchaos-release -Dgpg.skip=true verify` 通过（含 API 兼容检查）。
- `.mvn/maven.config` 中的 `revision` 与 `CHANGELOG.md` 的版本号一致，`chaos.release.compareVersion` 指向上一个已发布版本。
- `chaos-dependencies` 覆盖全部对外 artifact，且不含不存在的 artifact（架构测试保证）。
- 所有 autoconfigure 都存在 `AutoConfiguration.imports`。
- 不存在 `spring.factories`。
- 所有 starter 均无 Java 代码。
- 示例工程不直接依赖 autoconfigure。
- `CHANGELOG.md` 已更新。
- 版本兼容矩阵已确认。

## chaos-python 发布

`chaos-python/chaos-tracing` 是独立的 pip 包，不参与 Maven 构建，但**版本与 Java 框架保持一致**。

| 项 | 说明 |
| --- | --- |
| 坐标 | PyPI `chaos-tracing` |
| 版本 | 与 `.mvn/maven.config` 的 `-Drevision` 相同，由 `scripts/check-python-version.py` 在 CI 校验 |
| 兼容性 | `chaos-tracing X.Y.Z` 对应 chaos 框架 `X.Y.Z`；trace 传播格式遵循 W3C Trace Context，跨小版本兼容 |
| 质量门禁 | CI `python` job 执行 ruff、mypy（strict）、pytest 与版本一致性校验 |

版本变更：

```bash
# 改完 .mvn/maven.config 的 revision 后同步 Python 包版本
python3 scripts/check-python-version.py --fix
```

构建与发布（手工执行，尚未纳入自动化）：

```bash
cd chaos-python/chaos-tracing
python3 -m build
python3 -m twine upload dist/*
```

## SBOM

`chaos-release` profile 在 `package` 阶段生成 CycloneDX 物料清单（`target/*-cyclonedx.{json,xml}`），
随构件一起发布。CI 的 build job 会验证 SBOM 能正常生成，避免发布当天才发现插件配置有问题。

## 依赖漏洞门禁

- CodeQL 扫描本仓库源码，**不覆盖依赖的已知漏洞**。
- PR 上由 `actions/dependency-review-action` 拦截引入 high 及以上漏洞的依赖变更。
- 根 POM enforcer 的 `bannedDependencies` 直接禁止一批已知有严重漏洞或已被取代的坐标。
