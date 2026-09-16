# chaos-mybatis

## 职责

MyBatis Plus 分页、多租户、数据权限、审计字段、逻辑删除、乐观锁基础能力。

## 依赖方式

引入 chaos-mybatis-starter（通常与场景 starter `chaos-web-service-starter` 组合使用）。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-mybatis-starter</artifactId>
</dependency>
```

如果只复用实体基类或租户接口，可以直接依赖 `chaos-mybatis`。

## 配置

```yaml
chaos:
  mybatis:
    db-type: mysql
    id-generator:
      worker-id: 1
      datacenter-id: 1
    pagination:
      max-limit: 500          # 单页最大条数，<= 0 表示不限制（不推荐）
      overflow: false
    optimistic-lock:
      enabled: true           # 只对带 @Version 字段的实体生效
    tenant:
      enabled: true
      column: tenant_id
      missing-tenant-behavior: deny
      id-pattern: "[A-Za-z0-9_.:@-]{1,128}"   # 租户 ID 白名单，不匹配时拒绝执行 SQL
      ignore-table-prefixes:
        - sys_
      ignore-tables:
        - flyway_schema_history
    data-scope:
      enabled: true
      empty-condition-behavior: deny
      ignore-table-prefixes:
        - sys_
      ignore-tables:
        - flyway_schema_history
```

## 示例

```java
@TableName("biz_order")
public class OrderEntity extends BaseEntity {

    @TableId
    private Long id;

    private String orderNo;
}
```

默认主键策略为 MyBatis Plus `ASSIGN_ID`，也就是 `DefaultIdentifierGenerator` 雪花 ID。实体主键使用 `Long`/`long`/`String`，并且 `@TableId` 不指定 `type` 时，会走全局雪花 ID。旧表如需继续使用数据库自增，可以在实体上显式声明：

```java
@TableId(type = IdType.AUTO)
private Long id;
```

生产多节点建议显式配置 `worker-id` 和 `datacenter-id`，两者取值范围都是 `0-31`，同一套业务库内每个节点组合应保持唯一。不配置时使用 MyBatis Plus 默认的本机信息推导策略。

如需接入业务自己的号段、Leaf、Redis 或其他发号器，注册 Spring Bean 覆盖默认实现：

```java
@Bean
IdentifierGenerator identifierGenerator() {
    return entity -> idService.nextId();
}
```

默认 `TenantIdProvider` 从 `RequestContext` 读取租户。业务可以覆盖：

```java
@Bean
TenantIdProvider tenantIdProvider() {
    return () -> LoginTenantContext.currentTenantId();
}
```

数据权限扩展：

```java
@Bean
DataScopeProvider dataScopeProvider() {
    return scope -> {
        if ("dept".equals(scope)) {
            return Optional.of(new DataScopeCondition("dept_id", currentUserDeptIds()));
        }
        if ("owner".equals(scope)) {
            return Optional.of(DataScopeCondition.eq("o", "owner_id", currentUserId()));
        }
        return Optional.empty();
    };
}
```

新版扩展可以改写 `condition(DataScopeRequest request)`，直接读取当前主体、表名和 mapper 语句：

```java
@Bean
DataScopeProvider dataScopeProvider() {
    return new DataScopeProvider() {
        @Override
        public Optional<DataScopeCondition> condition(String scope) {
            return Optional.empty();
        }

        @Override
        public Optional<DataScopeCondition> condition(DataScopeRequest request) {
            if ("owner".equals(request.scope())) {
                return Optional.of(DataScopeCondition.eq("owner_id", request.subject().userId()));
            }
            if ("dept".equals(request.scope())) {
                return Optional.of(DataScopeCondition.in("dept_id", currentDeptIds(request.subject())));
            }
            return Optional.empty();
        }
    };
}
```

如果数据范围本身还需要经过 ABAC/RBAC 策略判断，可以在 Provider 中注入 `DataScopeAuthorizationService`，先判断 `request` 是否允许，再返回 SQL 条件。

业务方法通过 `@DataScope` 声明策略：

```java
@DataScope("dept")
public List<OrderVO> list(OrderQuery query) {
    return orderMapper.selectList(query.toWrapper());
}
```

开启后框架会注册 MyBatis Plus 官方 `DataPermissionInterceptor`，并根据 `DataScopeProvider` 返回的 `DataScopeCondition` 追加 SQL 条件：

```sql
dept_id in ('100', '101')
```

支持的条件能力：

| 条件 | 示例 |
| --- | --- |
| EQ | `DataScopeCondition.eq("owner_id", "1001")` |
| IN | `new DataScopeCondition("dept_id", List.of("100", "101"))` |
| LIKE | `DataScopeCondition.like("region_code", "3301%")` |
| BETWEEN | `DataScopeCondition.between("created_month", "202601", "202612")` |
| 表别名 | `DataScopeCondition.Rule.eq("o", "owner_id", "1001")` |
| 多字段 AND | `new DataScopeCondition(List.of(rule1, rule2))` |

框架通过 JSQLParser AST 构建条件，不拼接原始 SQL；字段名和表别名只允许字母、数字和下划线。

**字面量转义**：JSQLParser 的 `StringValue` 不会转义单引号，直接 `new StringValue(value)` 时 `x' OR '1'='1` 可以改写 SQL。框架统一通过 `SqlLiterals` 生成租户 ID 和数据权限值字面量：

- 所有数据库：`'` → `''`；
- MySQL 同类数据库（`DbType.mysqlSameType()`）：额外 `\` → `\\`，防止 `\'` 闭合字符串；
- 拒绝控制字符。

租户 ID 还会先经过 `chaos.mybatis.tenant.id-pattern` 白名单校验（与 chaos-core `RequestContextSnapshot` 规则一致），两层防护互为兜底。

当 `@DataScope` 已声明但 `DataScopeProvider` 没有返回有效条件时，默认追加永假条件：

```sql
1 = 0
```

如确需兼容旧系统，可设置 `chaos.mybatis.data-scope.empty-condition-behavior=ignore`。

租户 ID 缺失时默认 fail-fast 拒绝 SQL 改写，避免后端被直连或任务线程未设置租户时误查全量数据。迁移期如必须保持旧行为，可临时设置：

```yaml
chaos:
  mybatis:
    tenant:
      missing-tenant-behavior: ignore
```

### 审计字段与逻辑删除

`ChaosMetaObjectHandler` 不再依赖 chaos-security：

- `createdAt`、`updatedAt` 始终填充；
- 逻辑删除字段（`@TableLogic`）为 null 时填充全局 `logic-not-delete-value`（`@TableLogic` 字段没有 fill 属性，`strictInsertFill` 对它无效，旧版本插入后为 NULL，`deleted = 0` 查询查不到新数据）；
- `createdBy`、`updatedBy` 来自 `AuditorProvider`：容器中存在 chaos-security-api 的 `LoginUserProvider` Bean（chaos-security-starter 自动注册）时读登录用户（`LoginUserAuditorProvider`，未登录时回退），否则读 `RequestContext`（`RequestContextAuditorProvider`）。chaos-mybatis 只依赖 chaos-security-api，不再依赖 chaos-security。可注册自定义 `AuditorProvider` Bean。

### 分页上限

`PaginationInnerInterceptor` 默认 `maxLimit = 500`，请求 `size=100000000` 时按 500 查询，防止全表扫描或批量导出。接口层仍建议用 `@Max` 做更严格的限制。

### 乐观锁

默认注册 `OptimisticLockerInnerInterceptor`。需要乐观锁的实体继承 `VersionedBaseEntity`（带 `@Version Long version`），并在表中增加 `version bigint not null default 0`。没有直接给 `BaseEntity` 加 `@Version`，是为了不让已有表因缺少 `version` 列而全部报错。

真实 MySQL 集成测试：

```bash
./mvnw -pl chaos-data/chaos-mybatis -am -Pchaos-integration-test verify
```

## 扩展点

- 优先注册 Spring Bean 覆盖默认实现。
- 不在业务代码中依赖 autoconfigure 类。
- 不跨层调用 infra 实现，domain/application 只依赖接口。
- 覆盖 `TenantIdProvider` 可对接登录态、SaaS 租户上下文或网关注入的租户头。
- 覆盖 `DataScopeProvider` 可对接部门、组织、本人或自定义数据范围；新版 `DataScopeRequest` 包含 scope、当前主体、表名、mappedStatementId 和环境属性。
- `ignoreTables` 用于系统表、字典表、迁移表等无需租户隔离的表。

## 注意事项

- 拦截器顺序：多租户 → 数据权限 → 分页 → 乐观锁。
- 多租户拦截器早于分页拦截器注册，避免分页 SQL 漏租户条件。
- 数据权限拦截器依赖 chaos-security 的 `DataScopeContext`，classpath 缺少时自动跳过注册。
- 数据权限拦截器在多租户之后、分页之前注册，避免分页 SQL 漏数据权限条件。
- 嵌套 `@DataScope` 会在内层退出后恢复外层上下文，避免线程上下文被提前清空。
- 空数据权限条件默认拒绝访问，避免权限服务异常或未配置时误放行。
- 租户 ID 缺失默认拒绝访问；内部任务或批处理应显式设置租户上下文，或只对明确系统表配置 ignore。
- `chaos.mybatis.db-type` 默认 `mysql`，非 MySQL 项目应显式配置为对应 MyBatis Plus `DbType`。
- 默认主键策略为雪花 ID，数据库字段应使用 `BIGINT`；如果接口直接返回 64 位数字给前端，应注意 JavaScript 安全整数精度问题。
- 显式声明了 `@TableId(type = IdType.AUTO)` 的实体仍使用数据库自增，不会被全局雪花策略覆盖。
- 数据权限字段名和表别名会做白名单校验，权限服务不要返回表达式片段或函数调用。
- 系统表默认忽略 `sys_` 前缀；业务表不要随意使用该前缀。
- domain 层不要依赖 MyBatis Plus 注解，持久化实体应放在 infrastructure/persistence。
- 复杂 SQL、跨库查询或非标准字段建议通过 `ignoreTables` 忽略后在业务 mapper 中显式控制。
- MySQL 集成测试位于 `src/integration-test/java`，只在 `chaos-integration-test` profile 下运行。
