# chaos-autoconfigure

## 职责

全部 chaos 功能的 Spring Boot 自动装配集中在这一个模块（参照 `spring-boot-autoconfigure` 的组织方式）：

| 包 | 自动装配 |
| --- | --- |
| `com.michael.chaos.autoconfigure.web` | `ChaosWebAutoConfiguration` |
| `...service` | `ChaosServiceAutoConfiguration` |
| `...tenant` | `ChaosTenantAutoConfiguration` |
| `...audit` / `...audit.jdbc` | `ChaosAuditAutoConfiguration`、`ChaosAuditJdbcAutoConfiguration` |
| `...security` / `...security.redis` | `ChaosSecurityAutoConfiguration`、`ChaosSecurityRedisAutoConfiguration` |
| `...authorization` | `ChaosAuthorizationAutoConfiguration` |
| `...gateway` / `...gateway.nacos` | `ChaosGatewayAutoConfiguration`、`ChaosGatewayNacosAutoConfiguration` |
| `...cloud` / `...cloud.nacos` | `ChaosCloudAutoConfiguration`、`ChaosCloudNacosAutoConfiguration` |
| `...job` | `ChaosJobAutoConfiguration` |
| `...mq` | `ChaosMqAutoConfiguration`、`ChaosMqOutboxAutoConfiguration` |
| `...mybatis` | `ChaosMybatisAutoConfiguration` |
| `...redis` | `ChaosRedisAutoConfiguration` |
| `...storage` | `ChaosStorageAutoConfiguration` |
| `...support` | 生产安全检查：`ProductionSafety`、`ProductionSafetyEnforcer`、`@UnsafeForProduction` |

所有自动装配登记在唯一的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。

## 依赖方式

业务应用通过 `chaos-*-starter` 间接引入，不直接依赖本模块；库模块不得依赖本模块。

本模块把所有 chaos 功能库与三方框架声明为 `optional`，由 starter 决定真正引入哪些：

- 每个自动装配以类级 `@ConditionalOnClass` 声明其功能库（如 `com.michael.chaos.web.config.ChaosWebProperties`）与框架；
- 引用可选类型的 `@Bean` 放入带 `@ConditionalOnClass` 的嵌套配置类，或像授权服务器的 Redis/JDBC 存储那样，
  把引用可选类型的代码集中到只在类存在时才调用的辅助类（`AuthorizationRedisStores`、`AuthorizationJdbcStores`）；
- `@Bean` 方法签名（包括 `ObjectProvider<可选类型>` 泛型参数）会被 Spring 反射解析，不能直接出现可选类型；
- Servlet / 响应式专属装配分别声明 `@ConditionalOnWebApplication(SERVLET / REACTIVE)`（例如网关的 `@EnableWebFluxSecurity`
  在 Servlet 应用中会与 Servlet 安全配置冲突）。

`OptionalDependencyIsolationTest` 用隔离的 `URLClassLoader` 物理移除一组 jar（逐个三方依赖族、逐个 chaos 功能库、全部功能库），
在 none / servlet / reactive 三种应用类型下按真实应用方式导入全部自动装配并刷新上下文，防止出现 `NoClassDefFoundError`。
新增自动装配或可选依赖时必须保持该测试通过。

## 配置

```yaml
chaos:
  production-safety:
    enabled: true
    # 未设置时按 profiles 判断
    production-mode:
    profiles: prod,production,prd
    # 默认 true：发现危险默认实现时阻断启动；false 时只输出 WARN
    fail-fast: true
    # 迁移期逃生开关
    allow-unsafe-defaults: false
```

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 总开关 |
| `production-mode` | 空 | 显式声明生产模式；为空时按 `profiles` 判断 |
| `profiles` | `prod,production,prd` | 视为生产的 profile。K8s 等不设置 profile 的部署请显式配置 `production-mode=true` |
| `fail-fast` | `true` | 1.0.3 起默认阻断启动 |
| `allow-unsafe-defaults` | `false` | 迁移期临时放行 |

## 行为

生产模式下检测到以下危险兜底实现时，默认抛出 `IllegalStateException` 阻断启动：

- chaos-web：`InMemoryRateLimiter`、`InMemoryIdempotentRepository`
- chaos-tenant：`NoopTenantStatusProvider`
- 其他模块通过 `@UnsafeForProduction` 或 `ProductionSafety.checkUnsafeDefaultBeans` 声明的实现

旧版本只输出 WARN，服务会带着本地限流、Noop 撤销等缺陷上线，与"启动时拒绝"的承诺不符。

## 扩展点

```java
@Bean
@ConditionalOnMissingBean
@UnsafeForProduction(value = InMemoryFooRepository.class, message = "chaos-foo: 生产环境不能使用 InMemoryFooRepository")
public FooRepository fooRepository() {
    return new InMemoryFooRepository();
}

@Bean
public SmartInitializingSingleton chaosFooProductionSafetyChecker(Environment environment,
                                                                  ConfigurableListableBeanFactory beanFactory) {
    return new ProductionSafetyEnforcer(environment, beanFactory);
}
```

不使用注解时可调用 `ProductionSafety.checkUnsafeDefaultBeans(environment, beanFactory, Map.of(类名, 提示))`。
`warnUnsafeDefaultBeans` 为历史方法名，行为与 `checkUnsafeDefaultBeans` 相同（同样遵循 `fail-fast`）。

## 注意事项

- `ProductionSafetyEnforcer` 会把 CGLIB 代理的配置类还原为用户类后再扫描 `@Bean` 方法，`@Configuration` 默认的
  `proxyBeanMethods=true` 不会再导致漏检。
- 升级到 1.0.3 后，如果生产环境仍依赖内存/Noop 实现，启动会失败；请先接入 Redis 等生产实现，或临时开启 `allow-unsafe-defaults`。
