# chaos-tenant

## 职责

提供 SaaS 租户生命周期、状态、套餐和隔离模式核心契约。源码目录为 `chaos-tenant`，对外 artifactId 为 `chaos-tenant`。

该模块不依赖 Spring、数据库、Redis、MQ 或对象存储 SDK，只定义 framework 级租户治理端口。

## 依赖方式

业务应用优先通过 `chaos-tenant-starter`、`chaos-gateway-starter` 或 `chaos-web-service-starter` 间接引入。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-tenant</artifactId>
</dependency>
```

## 配置

```yaml
chaos:
  tenant:
    enabled: true
    fail-closed: true        # 推荐的生产默认值：租户状态未知时拒绝，避免租户中心异常导致越权
    servlet-filter:
      enabled: false         # Servlet 服务端租户状态校验，默认关闭
      exclude-paths:
        - /actuator/**
        - /error
```

### Servlet 租户校验

早期版本只有网关调用 `TenantAccessValidator`，绕过网关直接访问的 Servlet 服务完全不校验租户状态。
`TenantAccessServletFilter`（`com.michael.chaos.tenant.servlet`，Servlet / Spring Web 为可选依赖）补上这一环，
自动装配只负责注册。开启后：

- 过滤器顺序为 `-90`，位于 Spring Security 过滤器链（`-100`）之后，读取认证结果写入 `RequestContext` 的租户 ID；
- **不读取任何客户端请求头**；
- 拒绝时返回 `403` 和统一 JSON，不回显租户状态细节，并计入 `chaos.tenant.denied` 指标。

### 线程上下文清理

`TenantContext` 已接入 `ContextPropagation.clearAll()`，chaos-web 的 `TraceFilter` 在请求结束时兜底清理；
即使业务代码 `TenantContext.set` 后忘记清理，也不会串到下一个复用该线程的请求。

## 核心模型

| 类型 | 说明 |
| --- | --- |
| TenantStatus | 租户状态，包含 ACTIVE、FROZEN、DISABLED、EXPIRED、DELETED、UNKNOWN |
| TenantIsolationMode | 隔离模式，包含 SHARED_SCHEMA、SCHEMA、DATABASE |
| TenantPlan | 套餐编码、名称、到期时间和配额 |
| TenantDescriptor | 租户运行态描述 |
| TenantStatusProvider | 租户状态查询端口 |
| TenantAccessValidator | 租户访问决策服务 |
| TenantContext | 当前线程租户治理上下文 |

## 示例

```java
@Bean
TenantStatusProvider tenantStatusProvider(TenantRepository repository) {
    return tenantId -> repository.findById(tenantId)
            .map(tenant -> new TenantDescriptor(
                    tenant.id(),
                    tenant.status(),
                    new TenantPlan(tenant.planCode(), tenant.planName(), tenant.expiresAt(), tenant.quotas()),
                    tenant.isolationMode()
            ))
            .orElse(TenantDescriptor.unknown(tenantId));
}
```

## 与其他模块协作

- Gateway：入口处校验租户状态，不可访问租户返回 403。
- MyBatis：多租户插件优先读取 `TenantContext`，再回退到 `RequestContext`。
- Security：JWT 与 `LoginUser` 继续携带 tenantId，租户状态由 `TenantStatusProvider` 决策。
- Audit：租户拒绝会记录 `gateway.tenant.denied` 审计事件。

## 注意事项

- 默认 `NoopTenantStatusProvider` 只适合开发和单体样例；生产环境应替换为真实租户中心。
- 租户状态未知时建议 fail-closed，避免租户中心异常导致越权访问。
- 不要把租户持久化实体放进 `chaos-tenant`，数据库适配应放在业务系统或独立 adapter 模块。
- 生产模式下使用 `NoopTenantStatusProvider` 默认阻断启动，迁移期可配置 `chaos.production-safety.allow-unsafe-defaults=true`。
- 框架不负责创建租户表，也不提供租户后台管理 UI。
