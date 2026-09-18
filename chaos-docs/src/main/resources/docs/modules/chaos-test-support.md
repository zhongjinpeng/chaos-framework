# chaos-test-support

## 职责

面向**业务项目**的测试支持库。它解决基于 chaos 写测试时反复出现的几类样板代码：

| 能力 | 类 | 需要的可选依赖 |
| --- | --- | --- |
| 请求 / 租户上下文夹具，作用域结束自动恢复 | `test.context.ChaosTestContext`、`@WithChaosContext`、`ChaosContextExtension` | 无（有 chaos-tenant 时同步 `TenantContext`） |
| 构造 `LoginUser`、写入 Spring Security 上下文 | `test.security.TestLoginUsers`、`ChaosSecurityTestSupport` | chaos-security-api、spring-security-core |
| MockMvc 以指定 LoginUser 发起请求 | `test.security.ChaosMockMvcSecurity` | spring-security-test、spring-test |
| 不连 Redis 的 `RedisTemplate` | `test.redis.InMemoryRedisTemplates` | spring-data-redis |
| Testcontainers 镜像与 `@DynamicPropertySource` 属性注册 | `test.containers.ChaosContainers`、`ChaosTestProperties` | testcontainers（mysql / postgresql 模块按需） |
| 生产安全检查的 `ApplicationContextRunner` 辅助 | `test.boot.ProductionSafetyTestSupport` | spring-boot-test |
| 收集审计事件并断言（限流拒绝、权限拒绝是否留下记录） | `test.audit.CapturingAuditEventPublisher` | chaos-audit |

除 `chaos-core` 与 `junit-jupiter-api` 外，所有依赖都是 optional：项目用到哪个框架，对应辅助类才可用，本模块不会把框架反向带进测试 classpath。

## 引入

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-test-support</artifactId>
    <scope>test</scope>
</dependency>
```

版本由 `chaos-dependencies` 管理。**只能以 test scope 引入**，starter 与库模块不得依赖它（架构测试规则 6、8 校验）。

## 示例

上下文夹具：

```java
@WithChaosContext(tenantId = "tenant-a", userId = "1001")
class OrderApplicationServiceTest {

    @Test
    void shouldCreateOrderForCurrentTenant() {
        assertThat(service.create(command).tenantId()).isEqualTo("tenant-a");
    }

    @Test
    void shouldIsolateTenants() {
        try (ChaosTestContext.Scope ignored = ChaosTestContext.tenant("tenant-b").open()) {
            assertThat(service.list()).isEmpty();
        }
    }
}
```

`RequestContext`、`TenantContext` 是 ThreadLocal，测试里直接 `set` 忘记清理会造成“单跑通过、全量失败”的串号问题；
扩展在每个测试方法结束后一定恢复进入前的上下文。租户/用户 ID 仍经过生产代码的白名单校验。

登录用户：

```java
LoginUser reader = TestLoginUsers.user("1001").tenant("tenant-a").permissions("order:read").build();

mockMvc.perform(get("/api/orders").with(ChaosMockMvcSecurity.loginUser(reader)))
       .andExpect(status().isOk());

try (ChaosSecurityTestSupport.Scope ignored = ChaosSecurityTestSupport.withLoginUser(reader)) {
    assertThat(SecurityUtils.hasPermission("order:delete")).isFalse();
}
```

Authentication 的构造方式与生产 JWT 转换器一致：principal 为 `LoginUser`，角色映射为 `ROLE_xxx`，权限编码原样作为 authority。
MockMvc 需要挂载 Spring Security 过滤器链（`@WebMvcTest` 自动挂载；手动构建时 `apply(springSecurity())`）。

集成测试（放在 `src/integration-test/java`，类名 `*IT`，通过 `-Pchaos-integration-test` 运行）：

```java
@Testcontainers
@SpringBootTest
class OrderRepositoryIT {

    @Container
    static final MySQLContainer<?> MYSQL = ChaosContainers.mysql();

    @Container
    static final GenericContainer<?> REDIS = ChaosContainers.redis();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ChaosContainers.registerDataSource(registry, MYSQL);
        ChaosContainers.registerRedis(registry, REDIS);
    }
}
```

镜像版本在 `ChaosContainers` 中统一固定（`redis:7.4-alpine`、`mysql:8.4`、`postgres:16-alpine`），避免各服务使用 `latest` 导致结果漂移。

生产安全检查：

```java
ProductionSafetyTestSupport.productionMode(contextRunner)
        .run(context -> assertThat(context).hasFailed());
```

审计事件断言：

```java
CapturingAuditEventPublisher publisher = new CapturingAuditEventPublisher();
// ... 触发一次被拒绝的请求
assertThat(publisher.eventsOf("security.permission.denied")).hasSize(1);
```

## 注意事项

- `InMemoryRedisTemplates` 只模拟 `opsForValue().set/get` 与 `delete`；需要 Lua、过期、Hash 等真实语义时使用 Redis 容器。
- `@WithChaosContext` 只作用于执行测试方法的线程，测试内自建线程池需要走框架的上下文传播。
- 框架仓库内部的 `chaos-autoconfigure`、`chaos-security`、`chaos-authorization`、`chaos-gateway`、`chaos-security-redis` 测试同样使用本模块（test scope）：辅助类只允许有一份，不允许各测试各抄一遍。
