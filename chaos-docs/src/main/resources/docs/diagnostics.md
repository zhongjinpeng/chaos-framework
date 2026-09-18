# 启动诊断：启动报告、/actuator/chaos 与可操作的错误提示

使用方最常见的两个问题是"我引入的功能到底生效了没有"和"启动失败了该改什么"。chaos 在 `chaos-autoconfigure` 中提供三件工具：

1. **启动报告**：应用启动完成后在日志中输出一次，列出已启用的功能与关键配置、未启用的原因、诊断提示。
2. **`/actuator/chaos` 端点**：以 JSON 返回与启动报告相同的内容，便于运行期排查或接入巡检。
3. **可操作的错误提示**：框架抛出的启动失败与配置错误统一使用"问题 / 原因 / 怎么修"格式。

## 1. 启动报告

引入任意 chaos starter 即默认开启。下面是 example-gateway 在本地启动时的真实输出：

```text
Chaos 启动报告 | 应用 example-gateway | profile [default] | 生产模式 否 | fail-fast 开
  已启用（4）
    tenant           servlet-filter=false
    audit            publisher=LoggingAuditEventPublisher
    gateway          token.type=jwt, jwk-set-uri=http://localhost:9000, issuer-uri=http://localhost:9000, audiences=1, rate-limiter=InMemoryRateLimiter, trusted-proxies=0
    cloud-nacos
  未启用
    缺少依赖         web, application, audit-jdbc, security, security-redis, authorization, gateway-nacos, cloud, mybatis, redis, mq, mq-outbox, job, storage
  诊断（1）
    [INFO] production-safety：当前使用开发用实现 InMemoryRateLimiter，以生产 profile 启动时会被生产安全检查阻断
           怎么修：上线前引入 chaos-redis-starter 并配置 spring.data.redis.*，Redis 实现会自动替换这些兜底实现
  查看完整报告：暴露 actuator 端点 chaos（management.endpoints.web.exposure.include）后访问 /actuator/chaos
```

（功能列表随引入的 starter 变化；`jwk-set-uri` 等 URL 只显示 scheme、host、port。）

阅读方式：

| 区块 | 含义 |
| --- | --- |
| 抬头 | 应用名、激活的 profile、是否被识别为生产模式（决定生产安全检查是否生效）、fail-fast 是否开启 |
| 已启用 | 功能名与 starter 名一致（`web` ↔ `chaos-web-starter`）；后面是关键生效配置，URL 只保留 scheme/host/port，敏感键整体掩码 |
| 未启用 | 按原因归类：**缺少依赖**（没引对应 starter，通常是有意的）、**应用类型不符**（例如网关只在 WebFlux 应用生效）、**被配置关闭**、**被排除**、**未导入** |
| 诊断 | 内置规则与业务自定义规则的提示，按 ERROR → WARN → INFO 排序，每条都带"怎么修" |

"是否启用"以自动装配类是否注册为 Bean 为准，"未启用原因"直接取自 Spring Boot 的 `ConditionEvaluationReport`（与 `--debug` 输出同源），不会与真实装配结果不一致。

配置：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `chaos.diagnostics.startup-report.enabled` | `true` | 是否输出启动报告 |
| `chaos.diagnostics.startup-report.level` | `info` | `info` 或 `debug`；设为 `debug` 后只在 `logging.level.com.michael.chaos.StartupReport=debug` 时可见 |
| `chaos.diagnostics.startup-report.identifiers` | 空 | 追加到报告抬头的自定义标识，见下节 |

日志 logger 名固定为 `com.michael.chaos.StartupReport`，可以单独调整级别或输出目标。

### 自定义启动标识

报告抬头默认只有应用名、profile、生产模式和 fail-fast。排查线上问题时通常还要知道是哪个版本、哪个实例、哪个机房，
这些信息可以追加成抬头下的一行「标识」：

```text
Chaos 启动报告 | 应用 example-gateway | profile [prod] | 生产模式 是 | fail-fast 开
  标识  版本=1.4.2 | 构建号=3871 | 实例=gateway-7d9f6c | 可用区=cn-hangzhou-b
  已启用（4）
```

构建期就确定的静态值写配置：

```yaml
chaos:
  diagnostics:
    startup-report:
      identifiers:
        "[版本]": "@project.version@"     # Maven 资源过滤注入
        "[构建号]": "${BUILD_NUMBER:}"    # CI 环境变量
        region: cn-hangzhou
```

> 中文等非「小写字母 / 数字 / 短横线」的 key **必须**写成 `"[中文]"`。Spring Boot 的宽松绑定会把不认识的
> 字符从属性名里剥掉，直接写 `版本:` 会让整段 map 退化成一个字符串，启动时报
> `No converter found ... to type java.util.Map`。纯英文小写 key（如 `region`）不需要方括号。

只有运行期才知道的值（容器 hostname、Pod 名、可用区、灰度标签）注册 `ChaosStartupIdentifierContributor` Bean：

```java
@Bean
ChaosStartupIdentifierContributor deploymentIdentifiers(Environment environment) {
    return () -> Map.of(
            "实例", System.getenv().getOrDefault("HOSTNAME", "unknown"),
            "可用区", environment.getProperty("cloud.zone", "unknown"));
}
```

规则：

- 可以注册多个贡献者，按 Bean 顺序合并；同名 key **以配置为准**。这样线上临时要把某个标识改掉
  （比如把机房标成"灰度"）只需改配置，不必改代码重新发布。
- 贡献者抛异常只记 debug 日志，不影响启动，其余标识照常输出——标识是辅助信息，不该成为启动的新失败点。
- 值经过与其他配置相同的脱敏：键名含 `password` / `secret` / `token` 等字样时只显示掩码。
- 标识同时出现在启动日志和 `/actuator/chaos` 响应里，两处内容一致。

### 框架 banner

`chaos-autoconfigure` 内置了一个带框架版本号的 banner，**引入任一 starter 即生效，不需要配置**：

```
       __
   ___/ /  ___ ____  ___
  / __/ _ \/ _ `/ _ \/ _ \
  \__/_//_/\_,_/\___/___/

 :: Chaos Framework ::   (v1.0.0)
 :: Spring Boot ::       (v3.5.16)
 :: 应用 ::              demo-service  (v1.0.0-SNAPSHOT)
 :: profile ::           dev
```

版本号在框架构建期由 Maven 资源过滤烧入，因此显示的一定是**实际引入的**框架版本，
而不是某处手写的数字。

要换成自己的，按优先级从高到低有三条路：

| 想要 | 怎么做 |
| --- | --- |
| 用自己的 banner | 放 `src/main/resources/banner.txt`。应用自己的资源在 classpath 上排在依赖 jar 之前（可执行 jar 里 `BOOT-INF/classes` 先于 `BOOT-INF/lib`），因此它天然覆盖框架那份 |
| 指定任意位置 | `spring.banner.location=classpath:my/banner.txt`；写 `classpath:com/michael/chaos/banner.txt` 则是显式要框架那份（应用同时有自己的 `banner.txt` 时也照用框架的） |
| 一个都不要 | `spring.main.banner-mode=off` |

实现上，框架把同一份 banner 同时打包到 `classpath:banner.txt`（Spring Boot 未配置
`spring.banner.location` 时的默认查找位置）和 `classpath:com/michael/chaos/banner.txt`（显式引用用）。
不是在运行期改默认值：banner 在 `SpringApplication#run` 里于容器刷新之前就已打印，那时自动装配还没跑，
而能赶在它之前的 `EnvironmentPostProcessor` 需要 `spring.factories`（本仓库禁用）。

应用版本那一行（`${application.formatted-version}`）读的是 jar 的 `MANIFEST.MF` 里的
`Implementation-Version`。`maven-jar-plugin` 默认不写这个条目，所以 `chaos-boot-parent` 替业务方开了
`addDefaultImplementationEntries`；用自己公司级 parent（只 import BOM）的项目要自己配上，
否则那一段是空的。

## 2. `/actuator/chaos` 端点

端点默认访问级别为只读，并与其他 actuator 端点一样**默认不通过 HTTP 暴露**：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,chaos
```

```bash
curl http://localhost:8081/actuator/chaos
```

返回结构（节选）：

```json
{
  "application": "example-order-service",
  "activeProfiles": ["jwt-token"],
  "productionMode": false,
  "failFast": true,
  "features": [
    {"name": "web", "enabled": true, "category": "NONE", "reason": "",
     "settings": {"rate-limiter": "RedissonRateLimiter", "idempotent-repository": "RedissonIdempotentRepository"}},
    {"name": "gateway", "enabled": false, "category": "MISSING_DEPENDENCY",
     "reason": "@ConditionalOnClass did not find required class 'org.springframework.cloud.gateway.filter.GlobalFilter'", "settings": {}}
  ],
  "findings": [
    {"severity": "WARN", "feature": "redis", "problem": "chaos.redis.key-prefix 未配置……", "fix": "配置 chaos.redis.key-prefix=${spring.application.name}"}
  ]
}
```

**安全提示**：报告中的配置值已脱敏，但仍会暴露应用由哪些功能组成、授权服务器地址和实现类名。生产环境只应在管理端口（`management.server.port`）或内网开放，并与其他 actuator 端点一起纳入访问控制；网关的默认白名单只放行 `/actuator/health/**`，不会对外暴露该端点。

## 3. 诊断规则

### 内置规则

| 规则 | 级别 | 触发条件 | 怎么修 |
| --- | --- | --- | --- |
| Redis key 前缀 | WARN | 启用 redis 且 `chaos.redis.key-prefix` 为空 | `chaos.redis.key-prefix=${spring.application.name}` |
| 开发用实现提前提示 | INFO | 非生产模式下容器中有 `InMemoryRateLimiter` / `InMemoryIdempotentRepository` / `NoopJwtRevocationService` | 上线前引入 `chaos-redis-starter` |
| 网关签发方 / 受众 | WARN | 网关开启 JWT 验签但 `chaos.gateway.jwt.issuer-uri` 或 `audiences` 为空 | 配置 issuer-uri 与 audiences |
| 可信代理 | INFO / WARN | 配置了 `server.forward-headers-strategy` 却没有 `trusted-proxies`（INFO）；开启 `trust-identity-headers` 却没有可信代理（WARN） | 配置 `chaos.gateway.trusted-proxies` / `chaos.web.forwarding.trusted-proxies` |
| 生产安全被放宽 | WARN | 生产模式下 `chaos.production-safety.fail-fast=false` 或 `allow-unsafe-defaults=true` | 迁移完成后删除放宽配置 |
| 响应式应用混入 chaos-web | WARN | 网关应用类路径中存在 chaos-web | 网关服务移除 `chaos-web-starter` / `chaos-web-service-starter` |
| 动态授权策略来源缺失 | WARN | `chaos.security.access.policy-source=redis` 但容器中没有 `AuthorizationPolicySource` | 引入 `chaos-redis-starter`，或把 `policy-source` 改回 `config` |

### 自定义规则

注册 `ChaosDiagnosticRule` Bean 即可，结果会出现在启动报告与端点中：

```java
@Bean
ChaosDiagnosticRule orderTimeoutRule() {
    return context -> context.property("order.payment.timeout").isEmpty()
            ? List.of(new ChaosFeatureReport.Finding(
                    ChaosFeatureReport.Severity.WARN, "order",
                    "未配置 order.payment.timeout，将使用 30 分钟默认值",
                    "按业务 SLA 配置 order.payment.timeout"))
            : List.of();
}
```

规则约定：只读取环境与容器状态（`ChaosDiagnosticContext` 提供 `property`、`listProperty`、`hasBeanOfType` 等方法，不会触发 Bean 创建）；不要访问外部系统；抛出的异常会被忽略，不影响启动。

## 4. 启动失败与配置错误提示目录

框架抛出的配置类异常统一为 `ChaosDiagnosticException`（继承 `IllegalStateException`），消息格式：

```text
问题：……
原因：
  - ……
怎么修：
  - ……
```

| 场景 | 何时出现 | 行为 |
| --- | --- | --- |
| 生产安全检查未通过 | 生产模式下容器中存在开发用实现 | 阻断启动；列出每个违规实现、替换建议与 **Bean 名称**，以及 `fail-fast` / `allow-unsafe-defaults` 放行开关 |
| 授权服务器生产安全检查未通过 | 生产模式下授权服务器使用默认密钥、临时 JWK、localhost issuer、内存存储等 | 阻断启动；列出违规项和 `chaos.authorization.production-safety.*` 放行开关 |
| Servlet 与 WebFlux 运行栈混用 | Servlet 应用的类路径中存在 chaos-gateway（通常是同时引入了网关与业务服务 starter） | 阻断启动；说明网关/业务服务分别应移除哪个 starter；`chaos.diagnostics.web-stack-check.enabled=false` 可关闭 |
| 网关缺少 JWT 解码器 | `chaos.gateway.auth-enabled=true`、token 类型 JWT、开启验签，但未配置 `chaos.gateway.jwt.jwk-set-uri` 也没有自定义 `ReactiveJwtDecoder` | 阻断启动（此前会在运行期对所有请求返回 401） |
| 网关未限定签发方或受众 | 未配置 `chaos.gateway.jwt.issuer-uri` / `audiences` | 启动 WARN |
| opaque introspection 缺少凭据 | 配置了 `introspection-uri` 但缺少 `client-id` / `client-secret`（网关 `chaos.gateway.opaque-token.*`、资源服务 `chaos.security.opaque-token.*`） | 阻断启动，指出缺少的配置项 |
| outbox 表不可访问 | 存在 JDBC outbox 仓储，但表不存在或无权限 | 启动 WARN（不阻断），给出按数据库方言选择的建表脚本路径 `classpath:db/chaos-mq-outbox-schema-{mysql,postgresql,h2}.sql`；`chaos.diagnostics.outbox-schema-check.enabled=false` 可关闭 |
| 缺少租户上下文 | 多租户 SQL 执行时当前线程没有租户（默认 `missing-tenant-behavior=DENY`） | SQL 执行失败；说明常见来源（未认证、任务/消息线程未建立上下文）与处理方式 |
| 租户 ID 格式非法 | 租户 ID 不匹配 `chaos.mybatis.tenant.id-pattern` | SQL 执行失败；**不回显租户值本身**（可能是注入载荷），只给出长度与白名单 |

### 为什么不用 FailureAnalyzer

Spring Boot 3.5 只从 `META-INF/spring.factories` 加载 `FailureAnalyzer`，而本项目禁止 `spring.factories`（结构检查与架构测试都会拦截）。
可操作的指引已经写进 `ChaosDiagnosticException` 的消息，Spring Boot 启动失败时会原样输出在日志中，效果与 FailureAnalyzer 等价，
也不需要为此开例外。第三方代码可以通过 `ChaosDiagnosticException#getDiagnostic()` 拿到结构化的问题、原因与修复建议。
